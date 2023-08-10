package com.siyeh.ipp.opassign.assignment;

class PolyadicAssignment {
  void x(int i) {
    i *= 1<caret> + 2 + 3;
  }
}