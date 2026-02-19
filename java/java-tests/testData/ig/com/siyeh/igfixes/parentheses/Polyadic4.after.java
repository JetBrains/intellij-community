package com.siyeh.ipp.parentheses;

class Polyadic {
  boolean foo(int a, int b, int c) {
      /*3*/
      return a /*1*/ ^ b/*2*/ ^ c/*4*/;
  }
}