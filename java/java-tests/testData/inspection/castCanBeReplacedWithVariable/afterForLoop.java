// "Replace '(FooBar)foo' with 'foobar'" "true"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    for (int i = 0; i < 10; i++) {
      baz = foobar.baz;
    }
    foobar = null;
    return -1;
  }
}