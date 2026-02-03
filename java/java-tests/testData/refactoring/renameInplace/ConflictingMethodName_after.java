class Foo {
  void foo(){}
  void bar() {
    foo();
  }
}

class FooImpl extends Foo {
  void foo() {
    super.foo();
  }
}