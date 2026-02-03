package com.siyeh.igfixes.style.replace_with_string;

class Precedence2 {
  void foo() {
    long start = 0, end = 0;
    String string = "Time: " + (end - start) + ".";
  }
}