class Test {
  void test(int <flown1>x) {
    System.out.println(<caret>x);
  }

  void use(int <flown111>x) {
    if (x > 0) {
      throw new IllegalArgumentException();
    }
    test(<flown11>x);
  }

  void use2(int x) {
    if (x >= 0) {
      throw new IllegalArgumentException();
    }
    test(x);
    test(<flown12>12);
    test(-12);
  }
}