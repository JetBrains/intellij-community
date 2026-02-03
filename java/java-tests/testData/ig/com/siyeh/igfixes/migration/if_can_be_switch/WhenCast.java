package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  String getText(Object object) {
    final String result;
    i<caret>f (object instanceof String && (((String)object).startsWith("PU") || ((String)object).startsWith("PU1"))) {
      result = (String)object;
    }
    else if (object instanceof Character) {
      result = "Tuesday";
    }
    else {
      result = "Sunday";
    }

    return result;
  }
}