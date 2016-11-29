package use;

import foo.Bar;

import static foo.Foo.*;

public class Test {
  void test() {
    foo(1);
    Bar.<caret>foo("1");
  }
}
