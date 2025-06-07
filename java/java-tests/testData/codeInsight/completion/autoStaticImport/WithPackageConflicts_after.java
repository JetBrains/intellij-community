import static java.util.Objects.requireNonNull;

class Foo {
  void m() {
    requireNonNull(<caret>)
  }
}