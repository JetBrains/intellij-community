// "Qualify with Test.this" "false"
class Test {
  String myStr;
  class Foo extends Super {
    Foo(Test t) {
      super(t.my<caret>Str);
    }
  }
}

class Super {
  protected String myStr;
  Super(String s) {
  }
}