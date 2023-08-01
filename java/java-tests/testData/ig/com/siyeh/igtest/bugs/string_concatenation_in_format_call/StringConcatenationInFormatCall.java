package com.siyeh.igtest.bugs.string_concatenation_in_format_call;



public class StringConcatenationInFormatCall {
  static final String FORMAT = "%3d";

  void foo(int i, int[] data) {
    String.format(<warning descr="'format()' call has a String concatenation argument">"a" + "b" + i</warning>);
    String.format(<warning descr="'format()' call has a String concatenation argument">"c: " + i</warning>);
    String.format("c = " + "%s", "a" + i);
    String.format("i = " + FORMAT, i);
    String.format(<warning descr="'format()' call has a String concatenation argument">"a = " + getString()</warning>);
    String.format(<warning descr="'format()' call has a String concatenation argument">"data[0] = " + data[0]</warning>);
    String.format("i = " + (i < 0 ? "%4d" : "%3d"), i);
    String.format(<warning descr="'format()' call has a String concatenation argument">"i = " + (i < 0 ? "%4d" : i)</warning>, i);
    System.out.printf(<warning descr="'printf()' call has a String concatenation argument">"i=" + i</warning>);
  }
  
  native String getString();
}