package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {

  private static int test(Object obj)
  {
    obj.getClass();
    //comment1
    i<caret>f ( obj instanceof List) {
      //comment2
      return ((List) obj).size();
    } else if (obj instanceof Character) {
      return 1;
    }
    //comment3
    if (obj instanceof String) {
      //comment4
      return ((String) obj).length();
    }
    //comment5
    if (obj instanceof Double) {
      return 2;
    }
    if (obj instanceof Integer) {
      return 3;
    }
    if (obj instanceof Float) {
      return 4;
    }
    throw new IllegalArgumentException();
  }
}