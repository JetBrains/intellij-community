package com.siyeh.igtest.bugs.suspicious_array_cast;

class SuspiciousArrayCast {

  private Long[] longs = (<warning descr="Suspicious cast to 'Long[]'">Long[]</warning>)getNumbers();
  private Number[] numbers = (Number[])getNumbers();

  private Number[] getNumbers() {
    return new Number[]{1L, 2L, 4L};
  }

  void f() {
    java.util.List ssList = new java.util.LinkedList();
    ssList.add("a");
    String[] sArray = (String[]) ssList.toArray(new String[ssList.size()]);
  }
}