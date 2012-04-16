class Test {
  void foo(boolean flag, final String anObject, int... i) {
    if (flag) {
      foo(false, anObject, i);
    }
    System.out.println();
    foo(flag, anObject, i);
  }
}
