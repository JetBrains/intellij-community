class WideningToDouble {
  public static void main(String[] args) {
    long x = 1L << 54;
    double y = x + 1;
    boolean trueValue = <warning descr="Condition 'x == y' is always 'true'">x == y</warning>; // ASSIGNMENT
    System.out.println(trueValue); // prints "true"
  }

  void test() {
    int y = 2;
    if (<warning descr="Condition 'y == 2.1' is always 'false'">y == 2.1</warning>) {
      System.out.println("true");
    } else
    System.out.println("false");
  }
}