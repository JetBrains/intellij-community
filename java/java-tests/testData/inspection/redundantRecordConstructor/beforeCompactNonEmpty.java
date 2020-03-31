// "Remove constructor" "false"
record Foo(int x) {
  public <caret>Foo {
    if (x < 0) throw new IllegalArgumentException();
  }
}