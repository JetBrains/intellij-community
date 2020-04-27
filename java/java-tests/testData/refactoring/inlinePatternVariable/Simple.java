class Test {
  void test(Object obj) {
    if (obj instanceof String <caret>s) {
      System.out.println(s.trim());
    }
  }
}