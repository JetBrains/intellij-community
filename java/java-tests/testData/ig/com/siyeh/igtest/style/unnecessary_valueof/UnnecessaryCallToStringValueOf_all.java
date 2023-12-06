package com.siyeh.igtest.style.unnecessary_valueof;

public class UnnecessaryCallToStringValueOf_all {
  public static void main(String[] args) {
    String s = <weak_warning descr="Can be replaced with concatenation with empty string">String.valueOf</weak_warning>(1);
    s = 1 + <weak_warning descr="Can be replaced with concatenation with empty string">String.valueOf</weak_warning>(1);
    s = 1 + <weak_warning descr="Can be replaced with concatenation with empty string">Character.toString</weak_warning>('2');
    s = 2 + <weak_warning descr="Can be replaced with concatenation with empty string">String.valueOf</weak_warning>(1 + 1);
    s = <weak_warning descr="Can be replaced with concatenation with empty string">String.valueOf</weak_warning>(1 + 1).trim();
  }
}