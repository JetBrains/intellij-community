class Test {
  static class A {
    private int a;

    A(final int a) {}
  }

  static class B extends A {
    B() {
      super(<error descr="'a' has private access in 'Test.A'">a</error>);
    }
  }
}
