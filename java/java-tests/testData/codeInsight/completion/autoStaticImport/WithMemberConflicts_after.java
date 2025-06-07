package a;

class Foo {
  void requireNonNull(Object o) {

  }

  void m() {
    Objects.requireNonNull(<caret>)
  }
}