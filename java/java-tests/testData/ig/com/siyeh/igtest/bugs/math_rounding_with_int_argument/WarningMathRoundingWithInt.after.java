package com.siyeh.igtest.bugs.math_rounding_with_int_argument;

public class WarningMathRoundingWithInt {

  void foo() {
    System.out.println(Math.sin(1));
    System.out.println(expectPrimitive(Math.round(1)));
    System.out.println(expectPrimitive(1));
    System.out.println(expectObject((double) 1));
    System.out.println(expectPrimitive(1));
    System.out.println(expectPrimitive(1 + 1));

    System.out.println(StrictMath.sin(1));
    System.out.println(expectPrimitive(StrictMath.round(1)));
    System.out.println(expectObject((double) 1));
    System.out.println(expectPrimitive(1));
    System.out.println(expectObject((double) (1 + 1)));
  }

  private static Double expectObject(Double aDouble) {
    return aDouble;
  }

  private static double expectPrimitive(double aDouble) {
    return aDouble;
  }
}
