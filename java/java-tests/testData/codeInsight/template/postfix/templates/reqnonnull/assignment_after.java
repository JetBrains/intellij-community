class Foo {
  void m(String s) {
    Object obj;
    obj = java.util.Objects.requireNonNull(s)<caret>
  }
}