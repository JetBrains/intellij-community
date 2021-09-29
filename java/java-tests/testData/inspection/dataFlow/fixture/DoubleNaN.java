public class DoubleNaN {
  void test(double x, double y) {
    if (x > y) {}
    else if (x == y) {}
    else if (x < y) {}
    else {
      // x or y is NaN
    }
  }

  native double getSomeDouble();

  void testComparison() {
    double RPM = getSomeDouble();
    if (RPM > 0) {
      //code
    } else if (RPM <= 0) {//intellisense assumes always true
      //code
    } else {//can trigger
      //code
    }
  }

  void testComparison2(double RPM) {
    if (RPM > 0) {
      //code
    } else if (RPM <= 0) {//intellisense assumes always true
      //code
    } else {//can trigger
      //code
    }
  }

  void test() {
    double x = Double.NaN;
    double y = Double.NaN;
    if(<warning descr="Condition 'x >= y' is always 'false'">x >= y</warning>) {
      System.out.println("oops");
    }
  }

  void test2() {
    System.out.println(1.0 == Double.NaN);
    System.out.println(!(1.0 < Double.NaN));
  }
}