class Test {
  void foo() {
    <selection>new Runnable() {
      public void run() {}
    }</selection>.run();
  }
  
  void bar() {
    foo();
  }
}