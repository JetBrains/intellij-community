class Test {
  void foo(int a, double b) {
    if (((<caret>) (true ? a : b))) {

    }
  }
}