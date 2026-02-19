class Test {
  void test(int x) {
    int y;
    try {
      y = x;
    }
    catch (NumberFormatException nfe) <warning descr="Unreachable code">{
      throw new IllegalArgumentException();
    }</warning>
  }
}