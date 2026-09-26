package com.siyeh.igfixes.style.replace_with_string;

class Marathon {

  private static void test(boolean marathon) {
    String s1 = (marathon ? "AAA" : "BBB") + "CCC";
  }
}