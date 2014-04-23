class Test {

  interface A {
    A f();
  }
  interface B {}

  static abstract class C implements A, B {}
  static abstract class D implements A, B {}

  interface I<T> {
    <error descr="Invalid method declaration; return type required">m</error>(T arg);
  }

  void bar(C c) {
    foo(c, x -> x.f());
    foo(c, x -> x);
  }

  <T> void foo(T t1, I<T> t3) {}

}

class Test2 {

  interface F {
    <X>  <error descr="Invalid method declaration; return type required">m</error>();
  }

  void g() {}

  {
    F f = this::g;
  }
}
