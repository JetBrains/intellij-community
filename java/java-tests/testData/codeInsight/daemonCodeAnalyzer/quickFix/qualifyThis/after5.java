// "Qualify this expression with 'Test.Foo'" "true"
class Test {
  void foo(Foo m) {
  }


  class Foo {
    void bar() {
      new Runnable() {
        @Override
        public void run() {
          foo(Foo.this);
        }
      }.run();
    }
  }
}

