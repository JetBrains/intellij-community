package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  public static void test() {
    String character = aChar();
    String str = aString();
    i<caret>f (character.equals("A")) {
      str = str + 1;
    } else if (character.equals("B")) {
      str = str + 2;
    } else if (character.equals("C")) {
      str = str + 3;
    }
  }

  private static String aString() {
    return "A";
  }

  private static String aChar() {
    return "null";
  }
}