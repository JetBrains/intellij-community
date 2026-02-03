class Test {
  void test() {
    byte i = 1;
    test(<caret>-  -i);
  }

  void test(byte i) {
  }

  void test(int i) {
  }
}