class Test {
  void test(int foo) {
    long x = 1; long y = x * <caret>4294967296L;
  }
}