package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Simple {
  void foo(String[] source, Object[] target) {
    <caret>for (int k = 0/*declr*/; k <//condition
                                    5;//before update
                k++//after update
      ) { // can be converted to System.arraycopy()
      target[k] = source[k];//body
    }
  }
}