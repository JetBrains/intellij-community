package com.siyeh.igtest.controlflow.pointless_null_check;

import org.jetbrains.annotations.NotNull;

public class StringLength {
  void testPossibleNpe(String s1, String s2) {
    if (s1 != null && s2.equals(s1)) {}
  }

  void testStartsWith(String str) {
    if (<warning descr="Condition '!str.isEmpty()' covered by subsequent condition 'str.startsWith(...)'">!str.isEmpty()</warning> && str.startsWith("xyz")) {}
  }

  void test(String str) {
    if (<warning descr="Condition 'str.isEmpty()' covered by subsequent condition 'str.length() < 3'">str.isEmpty()</warning> || str.length() < 3) {}
  }

  void testNullcheck(String str) {
    if (str != null && str.equals("foo")) {}
  }

  void testNullcheck2(String str) {
    if (<warning descr="Condition 'str != null' covered by subsequent condition '\"foo\".equals(...)'">str != null</warning> && "foo".equals(str)) {}
  }

  void test2(String str) {
    if (str.length() > 8 && str.charAt(8) == 'a') {}
  }
}