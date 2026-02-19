class Test {
  void test(Object obj) {
    int <caret>x = 10;
    for (int i = 0; i < 10; i++) {
      int y = 20;
    }
  }
}
