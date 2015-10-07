
interface A<T> {
  void foo(T x);
  default void foo(String x) { }
}

class C implements A<String> {
  @Override
  public void foo(String x) {
    A.super.foo<error descr="Ambiguous method call: both 'A.foo(String)' and 'A.foo(String)' match">(x)</error>;
  }
}

interface A2<T>  {
  Object foo(T x);
  default Integer foo(String <warning descr="Parameter 'x' is never used">x</warning>) { return null; }
}

abstract class C2 {
  public void foo(A2<String> x) {
    x.foo<error descr="Ambiguous method call: both 'A2.foo(String)' and 'A2.foo(String)' match">("")</error>;
  }
}
