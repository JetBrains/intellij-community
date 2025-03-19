package com.siyeh.ipp.parentheses;

class Polyadic {
  boolean foo(int a, int b, int c) {
    return a /*1*/^ (b<caret>/*2*/ ^ c/*3*/)/*4*/;
  }
}