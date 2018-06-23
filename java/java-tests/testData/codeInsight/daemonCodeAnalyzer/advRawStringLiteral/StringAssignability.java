class Test {
  void m(String s) {}
  <T> void n(T s) {}
  {
    String s = ``abc``;
    m(`abc`);
    n(`abc`);
    String[] array = {``abc``};
  }
}