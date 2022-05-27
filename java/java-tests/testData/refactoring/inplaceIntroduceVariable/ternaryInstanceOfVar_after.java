class X {
  void test() {
    Object x = getXyz();
      String y;
      if (x instanceof String) {
          String x1 = (String) x;
          y = (String) x1;
      } else {
          y = null;
      }
  }
}