// "Inline variable" "true-preview"
package com.siyeh.igfixes.inline;

class ArrayInitializer {

  void m(String[] ts) {
    String[] <caret>ss = {"a", "b"};
    ts = ss;
  }
}