class X {
  public void test(String s) {
    System.out.println(s.trim());
  }
  
  void use() {
    test(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
}