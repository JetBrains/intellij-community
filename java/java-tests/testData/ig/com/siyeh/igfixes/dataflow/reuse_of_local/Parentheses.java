class Test {
  void test(int a, int b) {
    int i = a + b;
    System.out.println(i);
    (<caret>i) = a - b;
    System.out.println(i);
  }
}