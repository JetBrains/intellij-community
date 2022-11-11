// "Qualify this expression with 'Test'" "true-preview"
class Test {
  void foo(Test t){}
  class Foo {
    Foo() {
      foo(Test.this);
    }
  }
}

