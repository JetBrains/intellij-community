// "Qualify this expression with 'Test'" "true"
class Test {
  void foo(Test t){}
  class Foo {
    Foo() {
      foo(thi<caret>s);
    }
  }
}

