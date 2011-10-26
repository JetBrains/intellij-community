// "Qualify this expression with 'Test.Foo'" "false"
class Test {
  public void main() {
    new Foo() {
      void bar() {
        new Runnable() {
          @Override
          public void run() {
            foo(th<caret>is);
          }
        }.run();
      }
    }.toString();
  }

  void foo(Foo m) {
  }


  class Foo {

  }
}

