package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  private static int doesntWork(Object obj)
  {
    obj.getClass();
      <caret>return switch (obj) {
          case List list -> list.size();
          case Character c -> 1;
          case String s -> s.length();
          case Double v -> 2;
          case Integer i -> 3;
          case Float v -> 4;
          default -> throw new IllegalArgumentException();
      };
  }
}