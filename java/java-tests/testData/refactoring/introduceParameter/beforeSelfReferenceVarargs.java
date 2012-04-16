class Test {
  void foo(boolean flag, int... i) {
    if (flag) {
      foo(false, <selection>""</selection>, i);
    }
    System.out.println();
    foo(flag, i);
  }
}
