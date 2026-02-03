class Test {
    private final Extracted extracted = new Extracted();

    Test(){}

  void foo(int... f) {
      extracted.foo(f);
  }

  void bar() {
      extracted.foo(1, 2);
  }
}