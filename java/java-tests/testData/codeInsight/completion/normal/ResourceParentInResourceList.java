class MyClass {
  static class InnerResource implements AutoCloseable { }
}
class MyOuterResource implements AutoCloseable { }
class Main {
  void f() {
    try (My<caret> r) {
    }
  }
}