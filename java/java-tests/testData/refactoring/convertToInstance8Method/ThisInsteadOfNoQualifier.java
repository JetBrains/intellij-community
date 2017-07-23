class Bar {
  void f() {}

  private static void fo<caret>o(Bar bar) {
    bar.f();
    Runnable r = bar::f;
  }
}