package com.example.sqlinjection;


import org.checkerframework.checker.tainting.qual.Untainted;


final class TaintDepth {
  public static void test(@Untainted String clear, String dirty) {
    String dirty11 = dirty;
    String dirty111 = dirty11;
    String dirty1111 = dirty111;
    String dirty11111 = dirty1111;
    String dirty111111 = dirty11111;
    String dirty1111111 = dirty111111;
    String dirty11111111 = dirty1111111;
    String dirty111111111 = dirty11111111;
    String dirty1111111111 = dirty111111111;
    String dirty11111111111 = dirty1111111111;
    sink(<warning descr="Unknown string is used as safe parameter">dirty11111111111</warning>);
  }

  public static String next(String next) {
    return next;
  }

  public static void sink(@Untainted String string) {

  }
}