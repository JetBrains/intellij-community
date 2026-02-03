class Test {
  @FunctionalInterface
  interface I {
    void f() throws Exception;
  }

  void bar() {
    foo(new Runnable() {
        public void run() {
            System.out.println("");
            System.out.println("");
        }
    });
  }
  
  void foo(Runnable anObject) {
      anObject.run();
  }
}