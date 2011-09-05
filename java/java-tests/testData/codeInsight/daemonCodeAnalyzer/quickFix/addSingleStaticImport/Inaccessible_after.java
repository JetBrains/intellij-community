package impl;
import foo.Foo;

import static impl.FooImpl.foo;

public class FooImpl extends Foo {}
class Bar {
  void doSmth(FooImpl im) {
    foo();
  }
}