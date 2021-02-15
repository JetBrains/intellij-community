class X {
  void test(int[] arr) {
    arr.clone();
  }

  void test2(int[] arr) {
    arr.clone();
  }
  
  void call() {
    test(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
}