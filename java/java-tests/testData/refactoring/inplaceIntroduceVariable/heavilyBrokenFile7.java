class X {
  void test(boolean b) {
    test(<caret>false);
    test(switch ()false);
  }
}