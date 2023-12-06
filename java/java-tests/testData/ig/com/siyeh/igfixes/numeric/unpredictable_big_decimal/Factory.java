package com.siyeh.igfixes.numeric.unpredictable_big_decimal;

import java.math.BigDecimal;

class Factory {
  void foo(double val) {
    BigDecimal bd = new <caret>BigDecimal(val);
  }
}