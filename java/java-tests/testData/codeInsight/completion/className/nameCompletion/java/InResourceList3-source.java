class MyClass {
  static class MyResource implements AutoCloseable { }

  void f() {
    try (My<caret> r) {
    }
  }
}