class A {
  A(C c) {}

  static class B extends A {
    B() {
      super(new <error descr="Cannot reference 'C' before superclass constructor is called">C</error>());
    }

    B(int x) {
      super(<error descr="Non-static method 'getC()' cannot be referenced from a static context">getC</error>());
    }

    B(double x) {
      super(<error descr="Non-static field 'c' cannot be referenced from a static context">c</error>);
    }
  }

  class B1 extends A {
    B1() {
      super(new <error descr="Cannot reference 'C' before superclass constructor is called">C</error>());
    }

    B1(int x) {
      super(getC());
    }

    B1(double x) {
      super(c);
    }
  }

  private C getC() {
    return new C();
  }

  private C c = new C();

  private class C {}
}