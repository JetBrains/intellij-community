class Test {
  void foo(boolean flag, int i) {
    if (flag) {
      foo(false, i, <selection>i + 1</selection>);
    }
    System.out.println();
    foo(false, i);
  }
}
