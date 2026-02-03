// "Qualify with Test.this" "false"
class Test {
  class Foo extends Super {
    Foo() {
      super(my<caret>Str);
    }
  }
}

class Super {
  protected String myStr;
  Super(String s) {
  }
}