class Foo {
  void xxx(){}
  void bar() {
    xxx();
  }
}

class FooImpl extends Foo {
  void xxx() {
    super.xxx();
  }
}