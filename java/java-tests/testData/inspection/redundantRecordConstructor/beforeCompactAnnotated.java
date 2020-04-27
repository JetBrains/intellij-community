// "Remove constructor" "false"
record Foo(int x) {
  @Deprecated
  public <caret>Foo {
    
  }
}