package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {

  private static int test(Object obj)
  {
    obj.getClass();
    //comment1
      <caret>return switch (obj) {
          case List list ->
              //comment2
                  list.size();
          case Character c -> 1;

          //comment3
          case String s ->
              //comment4
                  s.length();

          //comment5
          case Double v -> 2;
          case Integer i -> 3;
          case Float v -> 4;
          default -> throw new IllegalArgumentException();
      };
  }
}