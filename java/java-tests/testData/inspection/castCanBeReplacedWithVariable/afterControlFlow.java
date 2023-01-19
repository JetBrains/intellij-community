// "Replace '(FooBar)foo' with 'foobar'" "true"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    if (baz == 0) {
      return foobar.baz;
    }
    return -1;
  }
}