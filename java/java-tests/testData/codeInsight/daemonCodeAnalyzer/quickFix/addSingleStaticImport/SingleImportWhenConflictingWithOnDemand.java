
import foo.Foo;

import static foo.Bar.foo;
import static foo.Foo.*;
class Test {
  void test() {
    Foo.f<caret>oo(1);
    foo("1");
  }
}
