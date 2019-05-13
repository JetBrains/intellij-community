class Test {

  interface A {
    <X> void m();
  }

  interface B {
    void m();
  }

  interface C extends A, B { }

  {
    C c = ()-> {};
  }
}

class Test1 {

  interface F {
    <X> void  m();
  }

  {
    F f = this::g;
  }

  void g() {}
}

class Test2 {

  interface F {
    <X> void  m();
    void a();
  }

  {
    F f = <error descr="Multiple non-overriding abstract methods found in interface Test2.F">() -> g()</error>;
  }

  void g() {}
}
