// "Replace '(FooBar)(foo)' with 'foobar'" "true-preview"

class FooBar {
  public int baz;

  int method(Object foo) {
    foobar = null;
    FooBar foobar = (FooBar)foo;
    return foobar.baz;
  }
}