package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  private static int doesntWork(Object obj)
  {
    obj.getClass();
    <caret>if (obj instanceof List) {
      return ((List) obj).size();
    } else if (obj instanceof Character) {
      return 1;
    }
    if (obj instanceof String) {
      return ((String) obj).length();
    }
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