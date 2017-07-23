import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findVirtual(Test.class, "method1", MethodType.genericMethodType(2));
    l.findVirtual(Test.class, "method1", MethodType.genericMethodType(2, false));
    l.findVirtual(Test.class, "method1", <warning descr="Cannot resolve method 'Object method1(Object, Object, Object[])'">MethodType.genericMethodType(2, true)</warning>);
    l.findVirtual(Test.class, "method1", <warning descr="Cannot resolve method 'Object method1(Object)'">MethodType.genericMethodType(1)</warning>);
    l.findVirtual(Test.class, "method1", <warning descr="Cannot resolve method 'Object method1(Object, Object, Object)'">MethodType.genericMethodType(3)</warning>);

    l.findVirtual(Test.class, "method2", MethodType.genericMethodType(2, true));
    l.findVirtual(Test.class, "method2", <warning descr="Cannot resolve method 'Object method2(Object, Object)'">MethodType.genericMethodType(2, false)</warning>);
    l.findVirtual(Test.class, "method2", <warning descr="Cannot resolve method 'Object method2(Object)'">MethodType.genericMethodType(1)</warning>);
    l.findVirtual(Test.class, "method2", <warning descr="Cannot resolve method 'Object method2(Object, Object[])'">MethodType.genericMethodType(1, true)</warning>);
    l.findVirtual(Test.class, "method2", <warning descr="Cannot resolve method 'Object method2(Object, Object)'">MethodType.genericMethodType(2)</warning>);
    l.findVirtual(Test.class, "method2", <warning descr="Cannot resolve method 'Object method2(Object, Object, Object)'">MethodType.genericMethodType(3)</warning>);
  }
}

class Test {
  public <T> T method1(T a, T b) {return null;}
  public <T> T method2(T a, T b, T... c) {return null;}
}