// "Qualify with Test.this" "true-preview"
class Test {
  String myStr;
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