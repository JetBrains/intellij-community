// "Replace '(FooBar)foo' with 'foobar'" "true-preview"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    return foobar.baz;
  }
}