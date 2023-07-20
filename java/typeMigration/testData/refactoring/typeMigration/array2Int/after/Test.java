class Test {

  int x() {
    return 1;
  }

  void y(int i) {}

  void z() {
    y(x());
  }
}