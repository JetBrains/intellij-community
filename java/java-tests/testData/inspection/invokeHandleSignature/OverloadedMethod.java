import java.lang.invoke.*;
import java.util.Arrays;
import java.util.List;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findVirtual(Test.class, "method", MethodType.methodType(void.class));
    l.findVirtual(Test.class, "method", MethodType.methodType(int.class, int.class));
    l.findVirtual(Test.class, "method", MethodType.methodType(boolean.class, short.class, char.class));
    l.findVirtual(Test.class, "method", MethodType.methodType(int.class, int.class, int[].class));
    l.findVirtual(Test.class, "method", MethodType.methodType(int.class, long.class, int[][].class));
    l.findVirtual(Test.class, "method", MethodType.methodType(int.class, long.class, int[][][].class));
    l.findVirtual(Test.class, "method", MethodType.methodType(Object.class, Object.class));
    l.findVirtual(Test.class, "method", MethodType.methodType(Object.class, Object[].class));
    l.findVirtual(Test.class, "method", MethodType.methodType(Object.class, Object[][].class));
    l.findVirtual(Test.class, "method", MethodType.genericMethodType(1));

    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(void)'">MethodType.methodType(void.class, void.class)</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(int)'">MethodType.methodType(void.class, int.class)</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'short method(char)'">MethodType.methodType(short.class, char.class)</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'int method(int[])'">MethodType.methodType(int.class, int[].class)</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'int method(int[][])'">MethodType.methodType(int.class, int[][].class)</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'Object[] method(Object[])'">MethodType.methodType(Object[].class, Object[].class)</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'Object[][] method(Object[][])'">MethodType.methodType(Object[][].class, Object[][].class)</warning>);

    l.<warning descr="Method 'method' is not static">findStatic</warning>(Test.class, "method", MethodType.methodType(void.class));
    l.findVirtual(Test.class, <warning descr="Cannot resolve method 'doesntExist'">"doesntExist"</warning>, MethodType.methodType(void.class));
  }

  void differentMethodTypeOverloads() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(void)'">MethodType.methodType(void.class, List.of(void.class))</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(void)'">MethodType.methodType(void.class, new Class<?>[]{void.class})</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(void)'">MethodType.methodType(void.class, Arrays.asList(void.class))</warning>);
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(void)'">MethodType.methodType(void.class, MethodType.methodType(String.class, void.class))</warning>);
    MethodType methodType = MethodType.methodType(String.class, MethodType.methodType(void.class, void.class));
    l.findVirtual(Test.class, "method", <warning descr="Cannot resolve method 'void method(void)'">MethodType.methodType(void.class, methodType)</warning>);
  }
}

class Test {
  public void method() {}
  public int method(int n) {return n;}
  public boolean method(short a, char b) {return true;}
  public int method(int n, int... a) {return n;}
  public int method(long n,  int[]... a) {return a.length;}
  public int method(long n, int[][][] a) {return a.length;}
  public Object method(Object o) {return o;}
  public Object method(Object[] o) {return o;}
  public Object method(Object[][] o) {return o;}
}