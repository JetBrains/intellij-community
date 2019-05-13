package use;

import foo.Bar;
import foo.Foo;

import static foo.Bar.foo;
import static foo.Foo.*;

public class Test {
  void test() {
    Foo.foo(1);
    foo("1");
  }
}
