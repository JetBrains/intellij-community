class C {
  void method() {}
  void method(String s) {}
  void method(String... s) {}
  void method1(String... s) {}

  {
    method(<caret>);
  }
}