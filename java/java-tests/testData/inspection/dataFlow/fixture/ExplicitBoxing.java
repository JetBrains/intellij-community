class X {
  void test(int x, int y) {
    if (Integer.valueOf(x).equals(y)) {
      System.out.println(<warning descr="Condition 'x == y' is always 'true'">x == y</warning>);
    }
    if (!Integer.valueOf(x).equals(y)) {
      System.out.println(<warning descr="Condition 'x == y' is always 'false'">x == y</warning>);
    }
    Integer xx = Integer.valueOf(x);
    if (xx.equals(y)) {
      System.out.println(<warning descr="Condition 'x == y' is always 'true'">x == y</warning>);
    }
    if (x == y) {
      System.out.println(<warning descr="Result of 'xx.equals(y)' is always 'true'">xx.equals(y)</warning>);
    }
    if (x != y) {
      System.out.println(<warning descr="Result of 'xx.equals(y)' is always 'false'">xx.equals(y)</warning>);
    }
  }

  void testDouble(double x, double y) {
    if(Double.valueOf(x).equals(y)) {
      // Can be false if x and y are NaNs
      System.out.println(x == y);
    }
    if(!Double.valueOf(x).equals(y)) {
      // Can be true if x and y are 0.0 and -0.0
      System.out.println(x == y);
    }
    if (x == y && Double.valueOf(x).equals(y)) {}
  }

  void test2(byte x, byte y) {
    if(<warning descr="Condition 'x != y && Byte.valueOf(x) == Byte.valueOf(y)' is always 'false'">x != y && <warning descr="Condition 'Byte.valueOf(x) == Byte.valueOf(y)' is always 'false' when reached">Byte.valueOf(x) == Byte.valueOf(y)</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void boxUnbox(int x) {
    if(<warning descr="Condition 'Integer.valueOf(x) > 5 && Integer.valueOf(x) < 0' is always 'false'">Integer.valueOf(x) > 5 && <warning descr="Condition 'Integer.valueOf(x) < 0' is always 'false' when reached">Integer.valueOf(x) < 0</warning></warning>) {}
  }
}