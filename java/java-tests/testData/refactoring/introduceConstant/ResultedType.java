class Test {
  void foo() {
    class C {}
    C c<caret> new C();
  }
}