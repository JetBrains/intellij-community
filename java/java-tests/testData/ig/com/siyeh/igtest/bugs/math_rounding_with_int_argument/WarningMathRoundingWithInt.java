package com.siyeh.igtest.bugs.math_rounding_with_int_argument;

public class WarningMathRoundingWithInt {

  void foo() {
    System.out.println(Math.sin(1));
    System.out.println(expectPrimitive(Math.<warning descr="'round()' with argument of type 'int'">round</warning>(1)));
    System.out.println(expectPrimitive(Math.<warning descr="'ceil()' with argument of type 'int'"><caret>ceil</warning>(1)));
    System.out.println(expectObject(Math.<warning descr="'floor()' with argument of type 'int'">floor</warning>(1)));
    System.out.println(expectPrimitive(Math.<warning descr="'rint()' with argument of type 'int'">rint</warning>(1)));
    System.out.println(expectPrimitive(Math.<warning descr="'rint()' with argument of type 'int'">rint</warning>(1+1)));

    System.out.println(StrictMath.sin(1));
    System.out.println(expectPrimitive(StrictMath.<warning descr="'round()' with argument of type 'int'">round</warning>(1)));
    System.out.println(expectObject(StrictMath.<warning descr="'ceil()' with argument of type 'int'">ceil</warning>(1)));
    System.out.println(expectPrimitive(StrictMath.<warning descr="'floor()' with argument of type 'int'">floor</warning>(1)));
    System.out.println(expectObject(StrictMath.<warning descr="'rint()' with argument of type 'int'">rint</warning>(1 + 1)));
  }

  private static Double expectObject(Double aDouble) {
    return aDouble;
  }

  private static double expectPrimitive(double aDouble) {
    return aDouble;
  }
}
