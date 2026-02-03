package com.siyeh.igfixes.numeric.unpredictable_big_decimal;

import java.math.BigDecimal;

class Factory {
  void foo(double val) {
    BigDecimal bd = BigDecimal.valueOf(val);
  }
}