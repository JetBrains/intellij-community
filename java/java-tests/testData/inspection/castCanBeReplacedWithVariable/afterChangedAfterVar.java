// "Replace '(FooBar)foo' with 'foobar'" "true-preview"

import java.lang.Object;

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    Object o = foobar.baz;
    foobar = null;
    return 0;
  }
}