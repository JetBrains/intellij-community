// "Replace '(FooBar)foo' with 'foobar'" "false"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    do {
      baz = ((FooBar<caret>)foo).baz + baz;
      foobar = null;
    } while (baz < 10);
    return -1;
  }
}