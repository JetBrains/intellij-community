class MyClass {
  static class MyResource implements AutoCloseable { }

  void f() {
    try (MyResource<caret> r) {
    }
  }
}