// "Qualify this expression with 'Test.Foo'" "true-preview"
class Test {
  void foo(Foo m) {
  }


  class Foo {
    void bar() {
      new Runnable() {
        @Override
        public void run() {
          foo(th<caret>is);
        }
      }.run();
    }
  }
}

