class A {
  {
    method(1);
    method(1, "a");
    method(1, "a", "b");
  }

  void m<caret>(String... args) {
    method(1, args);
  }

  void method(int i, String... args) {
  }
}