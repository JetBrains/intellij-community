// "Cast argument to 'List'" "true-preview"
class Test {
  void m(Object o) {
    foo(<caret>o);
  }

  private void foo(final java.util.List o) {}
}
