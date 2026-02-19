package com.siyeh.ipp.concatenation.merge_sequence;

class Parentheses2 {

  void foo(StringBuilder s) {
    s.append(1).app<caret>end(2);
    (s.append(3)).append(4);
  }
}