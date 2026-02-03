package com.siyeh.igfixes.style.replace_with_string;

class Marathon {

  private static void test(boolean marathon) {
    String s1 = new <caret>StringBuilder(marathon ? "AAA": "BBB").append("CCC").toString();
  }
}