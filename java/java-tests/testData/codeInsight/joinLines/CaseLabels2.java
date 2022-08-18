class A {
  void test(int x) {
    switch (x) {
      <caret>case 0:
      case /*hello*/ 1,2,3:
        System.out.println("hello");
      default:
    }
  }
}
