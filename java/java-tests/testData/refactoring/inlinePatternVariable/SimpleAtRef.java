class Test {
  void test(Object obj) {
    if (obj instanceof String s) {
      System.out.println(<caret>s.trim());
    }
  }
}