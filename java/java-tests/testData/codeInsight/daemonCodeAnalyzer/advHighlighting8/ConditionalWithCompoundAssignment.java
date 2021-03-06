class MyTest {
  static <T> T m() {
    return null;
  }

  void n(boolean b) {
    double ex = 0.0;
    ex <error descr="Operator '+' cannot be applied to 'double', 'java.lang.Object'">+=</error> (b ? MyTest.m() : 0);
    ex <error descr="Operator '*' cannot be applied to 'double', 'java.lang.Object'">*=</error> (b ? MyTest.m() : 0);

    boolean s = false;
    s <error descr="Operator '&' cannot be applied to 'boolean', 'java.lang.Object'">&=</error> (b ? MyTest.m() : true);
  }

}