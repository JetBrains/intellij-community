package com.siyeh.igtest.classlayout.non_final_field_in_enum;

enum Foo {
  FIRST("first"),
  SECOND("second");

  public String <warning descr="Non-final field 'str' in enum 'Foo'">str</warning>;

  public static String <warning descr="Non-final field 'strng' in enum 'Foo'">strng</warning> = "third";

  void bar() {
    strng = "fourth";
  }

  Foo(String str) {
    this.str = str;
  }
}