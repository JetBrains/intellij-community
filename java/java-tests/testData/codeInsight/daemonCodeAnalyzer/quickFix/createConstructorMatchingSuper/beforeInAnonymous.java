// "Create constructor matching super" "false"
public class Test {
   private Test() {}
}

class Foo {
  {
    new Test() {
      Te<caret>st() {
      }
    };
  }
}