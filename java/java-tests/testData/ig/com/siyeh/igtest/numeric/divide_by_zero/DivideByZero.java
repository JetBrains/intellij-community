package com.siyeh.igtest.numeric.divide_by_zero;

public class DivideByZero {

  int divide(int num) {
    return num / 3 / <warning descr="Division by zero">0</warning>;
  }

  int rest(int num) {
    return num % <warning descr="Division by zero">0</warning> % 1;
  }

  void assignment(int i, double d) {
    <warning descr="Division by zero">i /= 1-1</warning>;
    <warning descr="Division by zero">d %= 0</warning>;
    i /= d;
  }

  // IDEABKL-7552 Report inspection with the highest severity
  void test(int size) {
    if (size == 0) {
      int x = 42 / <warning descr="Division by zero">size</warning>;
    }
  }

  void test2(int size) {
    if (size > 0) {
      System.out.println(43 / size);
      return;
    }
    if (size < 0) {
      System.out.println(41 / size);
      return;
    }
    System.out.println(42 / <warning descr="Division by zero">size</warning>);
  }


  public static void main(String[] args, int d) {
    String is = null;
    if (d != 0) return;

    try {
      if (Math.random() > 0.5) {
        double k = 1 / <warning descr="Division by zero">d</warning>;
      } else {
        is = "This is printed half of the time";
        double k = 1 / <warning descr="Division by zero">0</warning>;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      if (is != null) {
        System.out.println(is);
      }
    }
  }
}