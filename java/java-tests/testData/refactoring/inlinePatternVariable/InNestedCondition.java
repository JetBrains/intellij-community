class Test {
  void test(Object obj) {
    if (Math.random() > 0.5 || (obj instanceof String <caret>s && s.isEmpty())) {
      System.out.println();
    }
  }
}