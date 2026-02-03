class Test {
  @FunctionalInterface
  interface I {
    void f() throws Exception;
  }

  void bar() throws Exception {
    foo();
  }
  
  void foo() throws Exception {
    <selection>System.out.println("");
    System.out.println("");</selection>
  }
}