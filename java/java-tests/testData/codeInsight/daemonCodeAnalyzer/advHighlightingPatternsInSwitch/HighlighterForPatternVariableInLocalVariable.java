class Main {
  void test(Object o) {
    boolean b = o instanceof ((String <caret>s) && s.length() > 1);
    s = "fsfsdfsd"; // unresolved
  }
}