/*
 * Compile with: javac -XDignore.symbol.file=true
 *
 * For the native part:
 * Windows:
 *   source /cygdrive/c/output/OpenJDK/output-jdk9-dev-dbg/configure-support/vs-env/set-vs-env.sh
 *   export PATH="$VS_PATH"
 *   export INCLUDE="$VS_INCLUDE"
 *   export LIB="$VS_LIB"
 *   cl -I c:/output/OpenJDK/output-jdk9-dev-dbg/images/jdk/include/ -I c:/output/OpenJDK/output-jdk9-dev-dbg/images/jdk/include/win32 TestI1.cpp -link -DLL -out:TestI1.dll
 * Linux:
 */

import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class Final extends ClassLoader {

  //
  // JLS (http://docs.oracle.com/javase/specs/jls/se8/html/index.html)
  // 4.12.4. final Variables: http://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.12.4

  /*
  static class Test0 {
    private final int f;
    public Test0() {
    }
  } // Compile time error: variable f might not have been initialized
  */

  static class TestA {
    private final int f = 0;
    public TestA(int f) {
      // this.f = f; // Compile time error: cannot assign a value to final variable f
    }
  }

  static class TestB {
    private final int f;
    {
      f = 0;      // Instance initializer
    }
    public TestB(int f) {
      // this.f = f; // Compile time error: cannot assign a value to final variable f
    }
  }

  static class TestC {
    private final int f;
    public TestC(int f) {
      this.f = f; // OK since Java 1.1 ("blank final")
    }
  }

  static class TestD {
    private final int f;
    public TestD(int f) {
      this.f = f;
      // this.f = f; // Compile time error: variable f might already have been assigned
    }
  }

  static class TestE {
    private final int f;
    public TestE(int f) {
      this.f = f;
      // this.f++; // Compile time error: variable f might already have been assigned
    }
  }

  static class TestF {
    private final int f;
    public TestF(int f) {
      if (f > 0) {
        this.f = f;
      } else {
        this.f = 42;
      }
    }
  }

  /*
  static class TestG {
    private final int f;
    public TestG(int f) {
      if (f > 0) {
        this.f = f;
      }
      if (f <= 0) {
        // this.f = 42; // Compile time error: variable f might already have been assigned
      }
    } // Compile time error: variable f might not have been initialized
  }

  static class TestG1 {
    public final int f;
    public TestG1(int f) {
      try {
        this.f = f;
      }
      catch (Exception e) {
        this.f = 42; // Compile time error: variable f might already have been assigned
      }
    }
  } // Compile time error: variable f might not have been initialized
  */

  // Try with resources
  static class TestG2 {
    public final int f;
    public TestG2(int f) {
      // No exections can occur here - only auto-closeable ressource will be closed
      try (java.io.StringReader sr = null) {
        this.f = f;
      }
    }
  }

  static class TestG3 {
    public final java.io.File f;
    public TestG3() throws java.io.IOException {
      this.f = java.io.File.createTempFile("x", "y");
    }
  }

  static void testG1_ASM() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "TestG1", null, "java/lang/Object", null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "f", "I", null, null);
    MethodVisitor constr = cw.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
    Label l_try_beg = new Label(), l_try_end = new Label();
    Label l_catch = new Label(), l_end = new Label();
    constr.visitCode();
    constr.visitVarInsn(ALOAD, 0);
    constr.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
    constr.visitLabel(l_try_beg);
    constr.visitVarInsn(ALOAD, 0);
    constr.visitVarInsn(ILOAD, 1);
    constr.visitFieldInsn(PUTFIELD, "TestG1", "f", "I");
    constr.visitLabel(l_try_end);
    constr.visitJumpInsn(GOTO, l_end);
    constr.visitLabel(l_catch);
    constr.visitVarInsn(ASTORE, 2);
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 42);
    constr.visitFieldInsn(PUTFIELD, "TestG1", "f", "I");
    constr.visitLabel(l_end);
    constr.visitInsn(RETURN);
    constr.visitTryCatchBlock(l_try_beg, l_try_end, l_catch, "java/lang/Exception");
    // max stack and max locals are automatically computed (because of the
    // 'ClassWriter.COMPUTE_FRAMES' option) in the ClassWriter constructor,
    // but we need this call nevertheless in order for the computation to happen!
    constr.visitMaxs(0, 0);
    constr.visitEnd();

    // Get the bytes of the class..
    byte[] b = cw.toByteArray();
    // ..and write them into a class file (for debugging)
    FileOutputStream fos = new FileOutputStream("TestG1.class");
    fos.write(b);
    fos.close();

    // Load the newly created class..
    Final f = new Final();
    Class<?> testG1Class = f.defineClass("TestG1", b, 0, b.length);
    // ..get the constructor..
    Constructor c = testG1Class.getConstructor(int.class);
    // ..and create a new "TestG1" object
    Object testG1 = c.newInstance(42);
    Field int_f = testG1Class.getDeclaredField("f");
    System.out.println("testG1.f = " + int_f.getInt(testG1));
  }

  static class TestH {
    private final int f;
    {
      foo();
    }
    public TestH(int f) {
      this.f = f;
      System.out.println(this + ".f = " + this.f);
    }
    public void foo() {
      System.out.println(this + ".f = " + this.f);
    }
  }

  static class TestH2 {
    private static final int f;
    static {
      f = foo();
    }
    public TestH2() {
      System.out.println(TestH2.class + ".f = " + f);
    }
    static int foo() {
      System.out.println(TestH2.class + ".f = " + f);
      return 42;
    }
  }

  // Native example

  static class TestI1 {
    public final int f;
    public TestI1(int f) {
      this.f = f;
    }
    public native void set(int f);
    static {
      System.loadLibrary("TestI1");
    }
  }

  // Unsafe example

  static class TestI {
    public final int f;
    public TestI(int f) {
      this.f = f;
    }
    static sun.misc.Unsafe UNSAFE;
    static {
      try {
        java.lang.reflect.Constructor<sun.misc.Unsafe> unsafeConstructor = sun.misc.Unsafe.class.getDeclaredConstructor();
        unsafeConstructor.setAccessible(true);
        UNSAFE = unsafeConstructor.newInstance();
      } catch (Exception e) {}
    }
    public void set(int f) throws Exception {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");
      UNSAFE.putInt(this, UNSAFE.objectFieldOffset(field), f);
    }
  }

  // Reflection example

  static class TestJ {
    public final int f;
    public TestJ(int f) {
      this.f = f;
    }
    public void set(int f) throws Exception {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");
      field.setAccessible(true);
      field.setInt(this, f);
    }
  }

  static class TestJ2 {
    public static final int f;
    static { f = 42; }
    public TestJ2() {
    }
    public void set(int f) throws Exception {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");

      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

      field.setInt(null, f);
    }
  }
  /*
    Without changing the modifiers, we get:

        Exception in thread "main" java.lang.IllegalAccessException: Can not set static final int field Final$TestJ2.f to (int)99
        at sun.reflect.UnsafeFieldAccessorImpl.throwFinalFieldIllegalAccessException(UnsafeFieldAccessorImpl.java:76)
        at sun.reflect.UnsafeFieldAccessorImpl.throwFinalFieldIllegalAccessException(UnsafeFieldAccessorImpl.java:100)
        at sun.reflect.UnsafeQualifiedStaticIntegerFieldAccessorImpl.setInt(UnsafeQualifiedStaticIntegerFieldAccessorImpl.java:129)
        at java.lang.reflect.Field.setInt(Field.java:950)
        at Final$TestJ2.set(Final.java:263)
        at Final.main(Final.java:698)

   */

  static class TestJ3 {
    public static final int f;
    static { f = 42; }
    public TestJ3() {
    }
    public static void set(String s) throws Exception {
      java.lang.reflect.Field field = TestJ3.class.getDeclaredField("f");

      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

      Field typeField = Field.class.getDeclaredField("type");
      typeField.setAccessible(true);
      typeField.set(field, String.class);

      field.set(null, s);
    }
    public static void main(String[] args) throws Exception {
      System.out.println("TestJ3.f = " + TestJ3.f);
      TestJ3.set("Volker");
      System.out.println("TestJ3.f = " + TestJ3.f);
    }
  }

  // MethodHandle example

  static class TestJ1 {
    public final int f;
    public TestJ1(int f) {
      this.f = f;
    }
    public void set(int f) throws Throwable {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");
      field.setAccessible(true);
      java.lang.invoke.MethodHandle setter = java.lang.invoke.MethodHandles.lookup().unreflectSetter(field);
      setter.invokeExact(this, f);
    }
  }

  // Constant inlining example

  static class TestM {
    public final int f;// = 42;
    public TestM() {
      this.f = 42;
    }
    public void set(int f) throws Exception {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");
      field.setAccessible(true);
      field.setInt(this, f);
    }
    public static void main(String args[]) throws Exception {
      TestM testM = new TestM();
      System.out.println((testM.f == 42 ? "Unchanged " : "Changed ") +
                         TestM.class.getDeclaredField("f").getInt(testM));
      testM.set(99);
      System.out.println((testM.f == 42 ? "Unchanged " : "Changed ") +
                         TestM.class.getDeclaredField("f").getInt(testM));
    }
  }

  static class TestK {
    public static final int f = 42;
    public TestK() { }
    public static void set(String field, Object val) throws Exception {
      java.lang.reflect.Constructor<sun.misc.Unsafe> unsafeConstructor = sun.misc.Unsafe.class.getDeclaredConstructor();
      unsafeConstructor.setAccessible(true);
      sun.misc.Unsafe unsafe = unsafeConstructor.newInstance();
      if (val instanceof Integer)
        unsafe.putInt(unsafe.staticFieldBase(TestK.class.getDeclaredField(field)),
                      unsafe.staticFieldOffset(TestK.class.getDeclaredField(field)), (Integer)val);
      else
        unsafe.putObject(unsafe.staticFieldBase(TestK.class.getDeclaredField(field)),
                         unsafe.staticFieldOffset(TestK.class.getDeclaredField(field)), val);
    }
    public static final TestK testK = new TestK();
    public static boolean harmless() {
      if (testK != null && testK.f == 42) { // Optimized by javac to if (testK != null && 42 == 42)
        return true;
      }
      // javap -p -s -c -constants Final\$TestK
      //
      // static final boolean $assertionsDisabled;
      // ...
      //  static {};
      //    descriptor: ()V
      //    Code:
      //       0: ldc           #31                 // class Final
      //       2: invokevirtual #32                 // Method java/lang/Class.desiredAssertionStatus:()Z
      //       5: ifne          12
      //       8: iconst_1
      //       9: goto          13
      //      12: iconst_0
      //      13: putstatic     #17                 // Field $assertionsDisabled:Z
      //
      //
      assert testK != null;
      System.out.println("Boom (testK, testK.f) = (" + testK + ", " + testK.f + ") expected (non-null, 42)");
      return false;
    }
  }

  static class Test {
    static final Test test = new Test(42);
    final int f;
    public Test(int f) {
      this.f = f;
    }
    static void set_test(Test val) throws Exception {
      java.lang.reflect.Constructor<sun.misc.Unsafe> unsafeConstructor = sun.misc.Unsafe.class.getDeclaredConstructor();
      unsafeConstructor.setAccessible(true);
      sun.misc.Unsafe unsafe = unsafeConstructor.newInstance();
      unsafe.putObject(unsafe.staticFieldBase(Test.class.getDeclaredField("test")),
                       unsafe.staticFieldOffset(Test.class.getDeclaredField("test")), val);
    }
    void set_f(int f) throws Exception {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");
      field.setAccessible(true);
      field.setInt(this, f);
    }
    public static int get_f() {
      if (test.f == 42) {
        return 42;
      }
      return test.f;
    }
    public static void main(String args[]) throws Exception {
      System.out.println(Test.get_f());      // 42

      Test.set_test(new Test(99));
      System.out.println(Test.get_f());      // 99

      Test test42 = new Test(42);
      Test.set_test(test42);
      for (int n = 0; n < 20_000; n++) {
        if (Test.get_f() != 42) System.out.println("!!!");
      }

      Test.set_test(new Test(99));
      System.out.println(Test.get_f());      // 42

      Test.test.set_f(99);
      System.out.println(Test.get_f());      // 42

      test42.set_f(99);
      System.out.println(Test.get_f());      // 99/42 -XX:-/+TrustFinalNonStaticFields
    }
  }


  static class Test_ {
    static Test_ test = new Test_(42);
    final int f;
    public Test_(int f) {
      this.f = f;
    }
    static void set_test(Test_ val) throws Exception {
      java.lang.reflect.Constructor<sun.misc.Unsafe> unsafeConstructor = sun.misc.Unsafe.class.getDeclaredConstructor();
      unsafeConstructor.setAccessible(true);
      sun.misc.Unsafe unsafe = unsafeConstructor.newInstance();
      unsafe.putObject(unsafe.staticFieldBase(Test_.class.getDeclaredField("test")),
                       unsafe.staticFieldOffset(Test_.class.getDeclaredField("test")), val);
    }
    void set_f(int f) throws Exception {
      java.lang.reflect.Field field = this.getClass().getDeclaredField("f");
      field.setAccessible(true);
      field.setInt(this, f);
    }
    public static int get_f() {
      if (test.f == 42) {
        return 42;
      }
      return test.f;
    }
    public static void main(String args[]) throws Exception {
      System.out.println(Test_.get_f());      // 42

      Test_.set_test(new Test_(99));
      System.out.println(Test_.get_f());      // 99

      Test_ test42 = new Test_(42);
      Test_.set_test(test42);
      for (int n = 0; n < 20_000; n++) {
        if (Test_.get_f() != 42) System.out.println("!!!");
      }

      Test_.set_test(new Test_(99));
      System.out.println(Test_.get_f());      // 42

      Test_.test.set_f(99);
      System.out.println(Test_.get_f());      // 42

      test42.set_f(99);
      System.out.println(Test_.get_f());      // 99/42 -XX:-/+TrustFinalNonStaticFields
    }
  }


  //
  // JVMS (http://docs.oracle.com/javase/specs/jvms/se8/html/index.html)
  //

  // ASM example

  static void testASM() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "TestGenerated", null, "java/lang/Object", null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "i", "I", null, null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "s", "Ljava/lang/String;", null, null);
    MethodVisitor constr = cw.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
    Label l1 = new Label(), l2 = new Label();
    constr.visitCode();
    constr.visitVarInsn(ALOAD, 0);
    constr.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
    constr.visitVarInsn(ILOAD, 1);
    constr.visitIntInsn(BIPUSH, 99);
    constr.visitJumpInsn(IF_ICMPLE, l1);
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 42);
    constr.visitFieldInsn(PUTFIELD, "TestGenerated", "i", "I");
    constr.visitLabel(l1);
    constr.visitVarInsn(ILOAD, 1);
    constr.visitIntInsn(BIPUSH, 99);
    constr.visitJumpInsn(IF_ICMPGE, l2);
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 43);
    constr.visitFieldInsn(PUTFIELD, "TestGenerated", "i", "I");
    constr.visitLabel(l2);
    constr.visitInsn(RETURN);
    // max stack and max locals are automatically computed (because of the
    // 'ClassWriter.COMPUTE_FRAMES' option) in the ClassWriter constructor,
    // but we need this call nevertheless in order for the computation to happen!
    constr.visitMaxs(0, 0);
    constr.visitEnd();

    // Get the bytes of the class..
    byte[] b = cw.toByteArray();
    // ..and write them into a class file (for debugging)
    FileOutputStream fos = new FileOutputStream("TestGenerated.class");
    fos.write(b);
    fos.close();

    // Load the newly created class..
    Final f = new Final();
    Class<?> testGeneratedClass = f.defineClass("TestGenerated", b, 0, b.length);
    // ..get the constructor..
    Constructor c = testGeneratedClass.getConstructor(int.class);
    // ..and create a new "TestGenerated" object
    Object testGenerated = c.newInstance(new Integer(99));
    Field int_f = testGeneratedClass.getDeclaredField("i");
    Field int_s = testGeneratedClass.getDeclaredField("s");
    System.out.println("testGenerated.i = " + int_f.getInt(testGenerated));
    System.out.println("testGenerated.s = " + (String)int_s.get(testGenerated));
  }

  /*
  static class TestX {
    public final int i;
    public final String s;
    public TestX() {
      this.i = 42;
      this.i = 43;
    }
  }

  error: variable i might already have been assigned
    this.i = 43;
          ^
  error: variable s might not have been initialized
    }
    ^
  */

  static void testX_ASM() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "TestX", null, "java/lang/Object", null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "i", "I", null, null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "s", "Ljava/lang/String;", null, null);
    MethodVisitor constr = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    Label l1 = new Label(), l2 = new Label();
    constr.visitCode();
    constr.visitVarInsn(ALOAD, 0);
    constr.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 42);
    constr.visitFieldInsn(PUTFIELD, "TestX", "i", "I");
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 43);
    constr.visitFieldInsn(PUTFIELD, "TestX", "i", "I");
    constr.visitInsn(RETURN);
    // max stack and max locals are automatically computed (because of the
    // 'ClassWriter.COMPUTE_FRAMES' option) in the ClassWriter constructor,
    // but we need this call nevertheless in order for the computation to happen!
    constr.visitMaxs(0, 0);
    constr.visitEnd();

    // Get the bytes of the class..
    byte[] b = cw.toByteArray();
    // ..and write them into a class file (for debugging)
    FileOutputStream fos = new FileOutputStream("TestX.class");
    fos.write(b);
    fos.close();

    // Load the newly created class..
    Final f = new Final();
    Class<?> testXClass = f.defineClass("TestX", b, 0, b.length);
    // ..get the constructor..
    Constructor c = testXClass.getConstructor();
    // ..and create a new "TestX" object
    Object testX = c.newInstance();
    Field int_f = testXClass.getDeclaredField("i");
    Field int_s = testXClass.getDeclaredField("s");
    System.out.println("testX.i = " + int_f.getInt(testX));
    System.out.println("testX.s = " + (String)int_s.get(testX));
  }

  static void testY_ASM() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "TestY", null, "java/lang/Object", null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "i", "I", null, null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "s", "Ljava/lang/String;", null, null);
    MethodVisitor constr = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    Label l1 = new Label(), l2 = new Label();
    constr.visitCode();
    constr.visitVarInsn(ALOAD, 0);
    constr.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 42);
    constr.visitFieldInsn(PUTSTATIC, "TestY", "i", "I");
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 43);
    constr.visitFieldInsn(PUTSTATIC, "TestY", "i", "I");
    constr.visitInsn(RETURN);
    // max stack and max locals are automatically computed (because of the
    // 'ClassWriter.COMPUTE_FRAMES' option) in the ClassWriter constructor,
    // but we need this call nevertheless in order for the computation to happen!
    constr.visitMaxs(0, 0);
    constr.visitEnd();

    // Get the bytes of the class..
    byte[] b = cw.toByteArray();
    // ..and write them into a class file (for debugging)
    FileOutputStream fos = new FileOutputStream("TestY.class");
    fos.write(b);
    fos.close();

    // Load the newly created class..
    Final f = new Final();
    Class<?> testYClass = f.defineClass("TestY", b, 0, b.length);
    // ..get the constructor..
    Constructor c = testYClass.getConstructor();
    // ..and create a new "TestY" object
    Object testY = c.newInstance();
    Field int_f = testYClass.getDeclaredField("i");
    Field int_s = testYClass.getDeclaredField("s");
    System.out.println("testY.i = " + int_f.getInt(testY));
    System.out.println("testY.s = " + (String)int_s.get(testY));
  }

  /*
  static class TestY {
    public static final int i;
    public final String s;
    public Test() {
      this.i = 42;
      this.i = 43;
    }
  }
error: cannot assign a value to final variable i
      this.i = 42;
          ^
error: cannot assign a value to final variable i
      this.i = 43;
          ^
  */

  public static interface I1 extends I2 {}
  public static interface I2 extends I3 {}
  public static interface I3            { public String getName(); }

  public static interface I4 {}

  public static class Helper {
    public static I4[] createI4Array() {
      I4 i4 = new I4() {  };
      return new I4[] { i4 };
    }
  }

  static void testASM3() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "TestX", null, "java/lang/Object", null);
    cw.visitField(ACC_PUBLIC | ACC_FINAL, "f", "Ljava/io/File;", null, null);
    MethodVisitor constr = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    Label l1 = new Label(), l2 = new Label();
    constr.visitCode();
    constr.visitVarInsn(ALOAD, 0);
    constr.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 42);
    constr.visitFieldInsn(PUTFIELD, "TestX", "i", "I");
    constr.visitVarInsn(ALOAD, 0);
    constr.visitIntInsn(BIPUSH, 43);
    constr.visitFieldInsn(PUTFIELD, "TestX", "i", "I");
    constr.visitInsn(RETURN);
    // max stack and max locals are automatically computed (because of the
    // 'ClassWriter.COMPUTE_FRAMES' option) in the ClassWriter constructor,
    // but we need this call nevertheless in order for the computation to happen!
    constr.visitMaxs(0, 0);
    constr.visitEnd();

    // Get the bytes of the class..
    byte[] b = cw.toByteArray();
    // ..and write them into a class file (for debugging)
    FileOutputStream fos = new FileOutputStream("TestX.class");
    fos.write(b);
    fos.close();

    // Load the newly created class..
    Final f = new Final();
    Class<?> testXClass = f.defineClass("TestX", b, 0, b.length);
    // ..get the constructor..
    Constructor c = testXClass.getConstructor();
    // ..and create a new "TestX" object
    Object testX = c.newInstance();
    Field int_f = testXClass.getDeclaredField("i");
    Field int_s = testXClass.getDeclaredField("s");
    System.out.println("testX.i = " + int_f.getInt(testX));
    System.out.println("testX.s = " + (String)int_s.get(testX));
  }

  public static void main(String args[]) throws Throwable {

    testG1_ASM();

    TestH h = new TestH(42);
    TestH2 h2 = new TestH2();

    TestI i = new TestI(42);
    i.set(99);
    System.out.println("i.f = " + i.f);

    TestI1 i1 = new TestI1(42);
    i1.set(99);
    System.out.println("i1.f = " + i1.f);

    TestJ j = new TestJ(42);
    j.set(99);
    System.out.println("j.f = " + j.f);

    TestJ1 j1 = new TestJ1(42);
    j1.set(99);
    System.out.println("j1.f = " + j1.f);

    TestJ2 j2 = new TestJ2();
    j2.set(99);
    System.out.println("j2.f = " + j2.f);

    TestJ3 j3 = new TestJ3();
    j3.set("Volker");
    System.out.println("j3.f = " + j3.f);

    TestM.main(null);

    Test.main(null);

    TestK.harmless();
    System.out.println("set f = 99");
    TestK.set("f", new Integer(99));
    TestK.harmless();
    System.out.println("set testK = null");
    TestK.set("testK", null);
    TestK.harmless();
    System.out.println("set testK = new TestK");
    TestK.set("testK", new TestK());
    TestK.harmless();
    System.out.println("compiling 'harmless()'");
    for (int n = 0; n < 20_000; n++) {
      TestK.harmless();
    }
    System.out.println("set testK = null");
    TestK.set("testK", null);
    TestK.harmless();

    System.out.println("TestK.testK   = " + TestK.testK);
    System.out.println("TestK.testK.f = " + TestK.testK.f); // Optimized by javac to System.out.println("TestK.testK.f = 42");
    System.out.println("TestK.testK.f = " + TestK.class.getDeclaredField("f").getInt(null));

    testASM();
    testX_ASM();
    testY_ASM();
  }
}
