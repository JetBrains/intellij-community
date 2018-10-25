class WideningToDouble {
  public static void main(String[] args) {
    long x = 1L << 54;
    double y = x + 1;
    boolean trueValue = <warning descr="Condition 'x == y' is always 'true'">x == y</warning>; // ASSIGNMENT
    System.out.println(trueValue); // prints "true"
  }
}