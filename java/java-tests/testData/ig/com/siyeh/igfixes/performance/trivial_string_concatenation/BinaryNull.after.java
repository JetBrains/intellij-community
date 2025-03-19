package com.siyeh.igfixes.performance.trivial_string_concatenation;

class BinaryNull {
  void foo() {
    String t = String.valueOf((Object) null);
  }
}