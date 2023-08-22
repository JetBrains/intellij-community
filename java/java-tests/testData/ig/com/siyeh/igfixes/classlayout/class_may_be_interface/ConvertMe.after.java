package com.siyeh.igfixes.classlayout.class_may_be_interface;

interface ConvertMe {

  String S = "";

  default void m() {}

  static void n() {
    new ConvertMe() {};
    class X implements ConvertMe {}
  }

  class A {}
}