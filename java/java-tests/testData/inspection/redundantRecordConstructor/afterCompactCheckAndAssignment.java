// "Remove statement" "true"
record Foo(int x) {
  public Foo {
    if (x < 0) {
      throw new IllegalArgumentException()
    }
  }
}