class Inverted {
  void test(boolean foo, boolean bar) {
    boolean b = foo<caret> ? bar : !bar;
  }
}