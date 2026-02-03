class X {
  void test(boolean b) {
      boolean b1 = false;
      test(b1);
    test(switch () b1);
  }
}