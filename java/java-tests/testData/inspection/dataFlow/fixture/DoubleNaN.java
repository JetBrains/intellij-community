public class DoubleNaN {
  void test() {
    double x = Double.NaN;
    double y = Double.NaN;
    if(<warning descr="Condition 'x >= y' is always 'false'">x >= y</warning>) {
      System.out.println("oops");
    }
  }

  void test2() {
    System.out.println(<warning descr="Condition '1.0 == Double.NaN' is always 'false'">1.0 == Double.NaN</warning>);
    System.out.println(!(<warning descr="Condition '1.0 < Double.NaN' is always 'false'">1.0 < Double.NaN</warning>));
  }
}