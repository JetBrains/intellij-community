public class DoubleNaN {
  void testNanCmp(double x) {
    if (<warning descr="Condition 'x > Double.NaN' is always 'false'">x > Double.NaN</warning>) {
      return;
    }
    boolean cmp = x > Double.NaN; // this is always false, a warning is expected
    System.out.println(x > Double.NaN); // this is always false, a warning is expected
  }

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