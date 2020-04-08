class Test {
  void test(boolean b, Object o1, Object o2) {
    if (!((b ? o1 : o2) instanceof String <caret>s)) return;
    System.out.println(s.isEmpty());
    System.out.println(s.trim().isEmpty());
  }
}