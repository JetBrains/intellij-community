// "Remove constructor" "true"
record Foo(int x) {
  public <caret>Foo {
    this.x = x;
  }
}