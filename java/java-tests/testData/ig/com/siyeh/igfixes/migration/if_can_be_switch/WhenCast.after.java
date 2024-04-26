package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  String getText(Object object) {
    final String result = switch (object) {
        case String s when (s.startsWith("PU") || s.startsWith("PU1")) -> s;
        case Character c -> "Tuesday";
        case null, default -> "Sunday";
    };<caret>

      return result;
  }
}