// "Qualify this expression with 'Test'" "false"
class Test {
  void foo(String t){}
  static class Foo {
    Foo() {
      foo(thi<caret>s);
    }
  }
}

