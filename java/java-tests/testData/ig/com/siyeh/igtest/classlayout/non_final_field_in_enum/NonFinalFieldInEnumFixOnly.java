package com.siyeh.igtest.classlayout.non_final_field_in_enum;

enum Bar {
  FIRST("first"),
  SECOND("second");

  public String <warning descr="Non-final field 'str' in enum 'Bar'">str</warning>;

  public static String strng = "third";

  void foo() {
    strng = "fourth";
  }

  Bar(String str) {
    this.str = str;
  }
}