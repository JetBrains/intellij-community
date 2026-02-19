class X {
  void test(String s) {
    final var xyz =  == null <caret>? null : s.trim();
  }
}