class Test {
  @FunctionalInterface
  interface I {
    void f() throws Exception;
  }

  void bar() throws Exception {
    foo(new I() {
        public void f() {
            System.out.println("");
            System.out.println("");
        }
    });
  }
  
  void foo(I anObject) throws Exception {
      anObject.f();
  }
}