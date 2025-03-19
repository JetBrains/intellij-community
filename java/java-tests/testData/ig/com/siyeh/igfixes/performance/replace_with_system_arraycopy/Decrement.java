package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Decrement {
  void foo(int[] a, int[] b) {
    <caret>for (int i = 1; i >= 0; i--) {
      b[i] = a[i];
    }
  }
}