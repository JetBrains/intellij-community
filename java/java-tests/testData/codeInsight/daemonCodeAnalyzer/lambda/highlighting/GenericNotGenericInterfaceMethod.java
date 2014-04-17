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
