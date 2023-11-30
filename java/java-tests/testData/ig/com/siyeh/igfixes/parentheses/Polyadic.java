package com.siyeh.ipp.parentheses;

class Polyadic {
  boolean foo(int a, int b, int c) {
    return (a <caret>== 1) && (b == 2) && (c != 3);
  }
}