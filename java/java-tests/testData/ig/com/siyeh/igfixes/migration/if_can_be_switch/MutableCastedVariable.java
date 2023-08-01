package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  public static void test(Object obj) {
    <caret>if (obj instanceof String) {
      String s = (String) obj;
      s = s.repeat(2);
      System.out.println(s);
      System.out.println((String) obj);
    } else if (obj instanceof Integer) {
      Integer i = (Integer) obj;
      System.out.println(i*2);
    } else {
      System.out.println();
    }
  }
}