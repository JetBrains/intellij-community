package use;

import foo.Bar;

import static foo.Foo.foo;

public class Test {
  void test() {
    foo(1);
    Bar.<caret>foo("1");
  }
}
