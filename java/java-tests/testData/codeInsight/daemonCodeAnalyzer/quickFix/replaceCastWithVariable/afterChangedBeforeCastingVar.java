// "Replace '(FooBar)foo' with 'foobar'" "true"

class FooBar {
  public int baz;

  int method(Object foo) {
    foo = null;
    FooBar foobar = (FooBar)foo;
    return foobar.baz;
  }
}