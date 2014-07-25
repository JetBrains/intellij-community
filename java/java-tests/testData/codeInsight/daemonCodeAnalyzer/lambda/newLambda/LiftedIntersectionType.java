class Test {

  interface A {
    A f();
  }
  interface B {}

  static abstract class C implements A, B {}
  static abstract class D implements A, B {}

  interface I<T> {
    T m(T arg);
  }

  void bar(C c) {
    foo(c, <error descr="Incompatible return type A in lambda expression">x -> x.f()</error>);
    foo(c, x -> x);
  }

  <T> void foo(T t1, I<T> t3) {}

}
