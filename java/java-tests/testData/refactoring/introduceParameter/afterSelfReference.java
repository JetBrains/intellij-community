class Test {
  void foo(boolean flag, int i, final int anObject) {
    if (flag) {
      foo(false, i, anObject);
    }
    System.out.println();
    foo(false, i, anObject);
  }
}
