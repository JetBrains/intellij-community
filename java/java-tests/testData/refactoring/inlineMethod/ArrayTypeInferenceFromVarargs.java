class Test {
  <T> void doSmth(T... ps) {
    System.out.println(ps);
  }

  void m() {
    doS<caret>mth(1, 2, 3);
  }
}