package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  public static void test() {
    String character = aChar();
    String str = aString();
      <caret>str = switch (character) {
          case "A" -> str + 1;
          case "B" -> str + 2;
          case "C" -> str + 3;
          default -> str;
      };
  }

  private static String aString() {
    return "A";
  }

  private static String aChar() {
    return "null";
  }
}