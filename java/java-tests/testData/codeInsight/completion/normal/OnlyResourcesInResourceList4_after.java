class MyClass {
  static class MyResource implements AutoCloseable { }

  void f() {
    try (MyResource r1 = null; MyResource<caret>) {
    }
  }
}