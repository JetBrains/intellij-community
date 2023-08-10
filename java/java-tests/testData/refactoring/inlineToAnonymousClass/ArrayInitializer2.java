class A {
  void test() {
    Inner[] data = new Inner[]{new Inner(5)};
  }

  static class Inner<caret> {
    Inner(int x) {}
  }
}