import java.lang.invoke.*;
import java.util.Arrays;
import java.util.List;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findSpecial(A.class, <warning descr="Method 'foo' is abstract in 'A'">"foo"</warning>, MethodType.methodType(void.class), A.class);
    l.findSpecial(A.class, <warning descr="Method 'foo' is abstract in 'A'">"foo"</warning>, MethodType.methodType(void.class), B.class);
    l.findSpecial(A.class, <warning descr="Method 'foo' is abstract in 'A'">"foo"</warning>, MethodType.methodType(void.class), C.class);

    l.findSpecial(B.class, "foo", MethodType.methodType(void.class), <warning descr="Caller class 'A' must be a subclass of 'B'">A.class</warning>);
    l.findSpecial(B.class, "foo", MethodType.methodType(void.class), B.class);
    l.findSpecial(B.class, "foo", MethodType.methodType(void.class), C.class);

    l.findSpecial(C.class, "foo", MethodType.methodType(void.class), <warning descr="Caller class 'A' must be a subclass of 'C'">A.class</warning>);
    l.findSpecial(C.class, "foo", MethodType.methodType(void.class), <warning descr="Caller class 'B' must be a subclass of 'C'">B.class</warning>);
    l.findSpecial(C.class, "foo", MethodType.methodType(void.class), C.class);

    l.findSpecial(A.class, "foo", <warning descr="Cannot resolve method 'double foo()'">MethodType.methodType(double.class)</warning>, A.class);

    l.findSpecial(A.class, "bar", MethodType.methodType(int.class), A.class);
    l.findSpecial(A.class, "bar", MethodType.methodType(int.class), B.class);
    l.findSpecial(A.class, "bar", MethodType.methodType(int.class), C.class);

    l.findSpecial(B.class, "bar", MethodType.methodType(int.class), <warning descr="Caller class 'A' must be a subclass of 'B'">A.class</warning>);
    l.findSpecial(B.class, "bar", MethodType.methodType(int.class), B.class);
    l.findSpecial(B.class, "bar", MethodType.methodType(int.class), C.class);

    l.findSpecial(C.class, "bar", MethodType.methodType(int.class), <warning descr="Caller class 'A' must be a subclass of 'C'">A.class</warning>);
    l.findSpecial(C.class, "bar", MethodType.methodType(int.class), <warning descr="Caller class 'B' must be a subclass of 'C'">B.class</warning>);
    l.findSpecial(C.class, "bar", MethodType.methodType(int.class), C.class);

    l.findSpecial(A.class, "bar", <warning descr="Cannot resolve method 'double bar()'">MethodType.methodType(double.class)</warning>, A.class);

    l.findSpecial(A.class, <warning descr="Cannot resolve method 'baz'">"baz"</warning>, MethodType.methodType(String.class, int.class), A.class);
    l.findSpecial(A.class, <warning descr="Cannot resolve method 'baz'">"baz"</warning>, MethodType.methodType(String.class, int.class), B.class);
    l.findSpecial(A.class, <warning descr="Cannot resolve method 'baz'">"baz"</warning>, MethodType.methodType(String.class, int.class), C.class);

    l.findSpecial(B.class, <warning descr="Method 'baz' is abstract in 'B'">"baz"</warning>, MethodType.methodType(String.class, int.class), <warning descr="Caller class 'A' must be a subclass of 'B'">A.class</warning>);
    l.findSpecial(B.class, <warning descr="Method 'baz' is abstract in 'B'">"baz"</warning>, MethodType.methodType(String.class, int.class), B.class);
    l.findSpecial(B.class, <warning descr="Method 'baz' is abstract in 'B'">"baz"</warning>, MethodType.methodType(String.class, int.class), C.class);

    l.findSpecial(C.class, "baz", MethodType.methodType(String.class, int.class), <warning descr="Caller class 'A' must be a subclass of 'C'">A.class</warning>);
    l.findSpecial(C.class, "baz", MethodType.methodType(String.class, int.class), <warning descr="Caller class 'B' must be a subclass of 'C'">B.class</warning>);
    l.findSpecial(C.class, "baz", MethodType.methodType(String.class, int.class), C.class);

    l.findSpecial(A.class, <warning descr="Cannot resolve method 'baz'">"baz"</warning>, MethodType.methodType(String.class, double.class), A.class);
    l.findSpecial(B.class, "baz", <warning descr="Cannot resolve method 'String baz(double)'">MethodType.methodType(String.class, double.class)</warning>, <warning descr="Caller class 'A' must be a subclass of 'B'">A.class</warning>);
  }

  void differentMethodTypeOverloads() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findSpecial(B.class, "baz", <warning descr="Cannot resolve method 'String baz(double)'">MethodType.methodType(String.class, double.class)</warning>, C.class);
    l.findSpecial(B.class, "baz", <warning descr="Cannot resolve method 'String baz(double)'">MethodType.methodType(String.class, List.of(double.class))</warning>, C.class);
    l.findSpecial(B.class, "baz", <warning descr="Cannot resolve method 'String baz(double)'">MethodType.methodType(String.class, Arrays.asList(double.class))</warning>, C.class);
    l.findSpecial(B.class, "baz", <warning descr="Cannot resolve method 'String baz(double)'">MethodType.methodType(String.class, new Class<?>[]{double.class})</warning>, C.class);
    l.findSpecial(B.class, "baz", <warning descr="Cannot resolve method 'String baz(double)'">MethodType.methodType(String.class, MethodType.methodType(void.class, new Class<?>[]{double.class}))</warning>, C.class);
  }
}

interface A {
  void foo();
  default int bar() {return 1;}
}

abstract class B implements A {
  public void foo() {}
  public int bar() {return 2;}
  abstract String baz(int n);
}

class C extends B {
  public void foo() {}
  public int bar() {return 3;}
  String baz(int n) {return "" + n;}
}
