class Test {
  void foo(Runnable anObject) {
    anObject.run();
  }
  
  void bar() {
    foo(() -> {
    });
  }
}