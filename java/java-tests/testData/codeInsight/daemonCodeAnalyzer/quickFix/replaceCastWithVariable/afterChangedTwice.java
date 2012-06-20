// "Replace '(FooBar)foo' with 'foobar'" "true"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    foobar = null;
    foo = null;
    foobar = (FooBar)foo;
    return foobar.baz;
  }
}