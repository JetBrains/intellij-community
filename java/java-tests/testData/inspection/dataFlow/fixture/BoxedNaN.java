class Fun {
  public static void main(String[] args) throws Exception {
    Double a = Double.NaN;
    boolean b = <warning descr="Condition 'a == a' is always 'true'">a == a</warning>;

    double c = a;
    boolean d = <warning descr="Condition 'c == c' is always 'false'">c == c</warning>;
  }

}