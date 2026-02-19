package com.siyeh.igfixes.classlayout.class_may_be_interface;

abstract class <caret>ConvertMe {

  public static final String S = "";

  public void m() {}

  public static void n() {
    new ConvertMe() {};
    class X extends ConvertMe {}
  }

  public class A {}
}