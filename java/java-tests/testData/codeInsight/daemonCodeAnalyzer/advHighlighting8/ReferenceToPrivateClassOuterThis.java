class A {
  A(C c) {}

  static class B extends A {
    B() {
      super(<error descr="'A.this' cannot be referenced from a static context">A.this</error>.new C());
    }

    B(int x) {
      super(<error descr="'A.this' cannot be referenced from a static context">A.this</error>.getC());
    }

    B(double x) {
      super(<error descr="'A.this' cannot be referenced from a static context">A.this</error>.c);
    }
  }

  class B1 extends A {
    B1() {
      super(A.this.new C());
    }

    B1(int x) {
      super(A.this.getC());
    }

    B1(double x) {
      super(A.this.c);
    }
  }

  private C getC() {
    return new C();
  }

  private C c = new C();

  private class C {}
}