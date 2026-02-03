
import foo.Foo;

import static foo.Bar.foo;
import static foo.Foo.*;
import static foo.Foo.foo;

class Test {
  void test() {
    foo(1);
    foo("1");
  }
}
