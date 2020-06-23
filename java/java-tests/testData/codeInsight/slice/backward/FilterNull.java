class Test {
  void test(String <flown1>x) {
    System.out.println(<caret>x);
  }

  void use(String <flown111>x) {
    test(<flown11>x);
    System.out.println(x.trim());
    test(x);
  }

  void use2(String x) {
    test(x.trim());
  }
}