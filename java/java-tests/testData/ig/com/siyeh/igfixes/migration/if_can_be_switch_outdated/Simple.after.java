package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  public static void main(String[] args) {
    Object o = new Object();
      <caret>switch (o) {
          case String s -> System.out.println("1");
          default -> {
          }
      }
  }
}