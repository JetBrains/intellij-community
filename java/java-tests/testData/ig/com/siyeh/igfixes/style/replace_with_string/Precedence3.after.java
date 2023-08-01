package com.siyeh.igfixes.style.replace_with_string;

class Precedence3 {
  void foo() {
    String start = "0", end = "0";
    String string = "Time: " + end + start + ".";
  }
}