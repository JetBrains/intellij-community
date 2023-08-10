class Fun {
  public static void main(String[] args) throws Exception {
    Double a = Double.NaN;
    Double a1 = a;
    boolean b = <warning descr="Condition 'a == a' is always 'true'">a == a</warning>;
    boolean b1 = <warning descr="Result of 'a.equals(a1)' is always 'true'">a.equals(a1)</warning>;
    boolean b2 = <warning descr="Result of 'a.equals(Double.NaN)' is always 'true'">a.equals(Double.NaN)</warning>;
    boolean b3 = a == Double.NaN;

    double c = a;
    boolean d = <warning descr="Condition 'c == c' is always 'false'">c == c</warning>;
  }

}