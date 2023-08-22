class Test {
  void test() {
    enum P {
      a, b, c;
    }
    P p = null;
      if (p == P.a || p == P.b) {
      } else if (p == P.c) {
      } else if (p == null) {
          throw new NullPointerException();
      }
  }
}