package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  public static void main(String[] args) {
    Object o = new Object();
    if<caret> (o instanceof String s && s.isEmpty()) {
      System.out.println("1");
    }
  }
}