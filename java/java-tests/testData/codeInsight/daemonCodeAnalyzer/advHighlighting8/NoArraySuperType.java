class X {
  void test(boolean flag) {
    int a = (flag ? new Object[0] : new int[0]).<error descr="Cannot resolve symbol 'length'">length</error>;
  }
}