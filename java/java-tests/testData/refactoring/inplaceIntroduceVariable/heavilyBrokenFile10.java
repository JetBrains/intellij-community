class X {
  void test() {
    System.out.println(<caret>this);
    this.foo bar = baz;
    this.foo = "xyz";
  }
}