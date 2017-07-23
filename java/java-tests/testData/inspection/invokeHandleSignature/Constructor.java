import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    Class c = Test.class;
    l.findConstructor(Test.class, MethodType.methodType(void.class));
    l.findConstructor(Test.class, MethodType.methodType(void.class, int.class));
    l.findConstructor(Test.class, MethodType.methodType(void.class, int.class, String.class));
    l.findConstructor(Test.class, MethodType.methodType(void.class, int.class, String[].class));
    l.findConstructor(Test.class, MethodType.methodType(void.class, String[][].class));
    l.findConstructor((c), MethodType.methodType(void.class));

    l.findConstructor(Test.class, <warning descr="Cannot resolve constructor 'int Test()'">MethodType.methodType(int.class)</warning>);
    l.findConstructor(Test.class, <warning descr="Cannot resolve constructor 'Test Test()'">MethodType.methodType(Test.class)</warning>);
    l.findConstructor(Test.class, <warning descr="Cannot resolve constructor 'Test Test(int)'">MethodType.methodType(Test.class, int.class)</warning>);
    l.findConstructor(Test.class, <warning descr="Cannot resolve constructor 'Test(String)'">MethodType.methodType(void.class, String.class)</warning>);
    l.findConstructor(Test.class, <warning descr="Cannot resolve constructor 'Test(int, String[][])'">MethodType.methodType(void.class, int.class, String[][].class)</warning>);
    l.findConstructor(Test.class, <warning descr="Cannot resolve constructor 'Test(String[])'">MethodType.methodType(void.class, String[].class)</warning>);

    l.findConstructor(WithDefault.class, MethodType.methodType(void.class));
    l.findConstructor(Class.forName("WithDefault"), MethodType.methodType(void.class));
    l.findConstructor(NoDefault.class, <warning descr="Cannot resolve constructor 'NoDefault()'">MethodType.methodType(void.class)</warning>);
    l.findConstructor(Class.forName("NoDefault"), <warning descr="Cannot resolve constructor 'NoDefault()'">MethodType.methodType(void.class)</warning>);

  }
}

class Test {
  public Test() {}
  public Test(int a) {}
  public Test(int a, String b) {}
  public Test(int a, String... b) {}
  public Test(String[]... b) {}
}

class WithDefault {
}

class NoDefault {
  public NoDefault(int n) {}
}