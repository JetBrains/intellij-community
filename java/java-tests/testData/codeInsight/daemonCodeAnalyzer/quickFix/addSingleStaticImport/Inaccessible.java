package impl;
import foo.Foo;
public class FooImpl extends Foo {}
class Bar {
  void doSmth(FooImpl im) {
    im.f<caret>oo();
  }
}