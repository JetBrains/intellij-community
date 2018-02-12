public class DoubleNaN {
  void test() {
    double x = Double.NaN;
    double y = Double.NaN;
    if(<warning descr="Condition 'x >= y' is always 'false'">x >= y</warning>) {
      System.out.println("oops");
    }
  }
}