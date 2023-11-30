package com.siyeh.ipp.concatenation.merge_sequence;

class Parentheses2 {

  void foo(StringBuilder s) {
      s.append(1).append(2).append(3).append(4);
  }
}