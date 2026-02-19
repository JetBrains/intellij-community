package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Decrement {
  void foo(int[] a, int[] b) {
      System.arraycopy(a, 0, b, 0, 2);
  }
}