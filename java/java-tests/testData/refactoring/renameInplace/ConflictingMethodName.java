class Foo {
  void foo(){}
  void bar() {
    foo();
  }
}

class FooImpl extends Foo {
  void f<caret>oo() {
    super.foo();
  }
}