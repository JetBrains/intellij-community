package com.siyeh.ipp.opassign.assignment;

class Conditional {
  void x(int i) {
    i *= 1<caret>==1 ? 2 : 3;
  }
}