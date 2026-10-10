class A {
  void <caret>gone() {}

  void f() {
    // gone() is not used any more
    String name = "gone";
  }
}
