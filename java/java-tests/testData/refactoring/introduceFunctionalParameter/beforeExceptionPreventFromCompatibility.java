class Test {
  @FunctionalInterface
  interface I {
    void f() throws Exception;
  }

  void bar() {
    foo();
  }
  
  void foo() {
    <selection>System.out.println("");
    System.out.println("");</selection>
  }
}