package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Simple {
  void foo(String[] source) {
    int[] src = new int[]{1, 2, 3};
    int offset = 10;
    int[] dst = new int[12];
      if (src.length - offset >= 0) System.arraycopy(src, 0 + offset, dst, 0, src.length - offset);
  }
}