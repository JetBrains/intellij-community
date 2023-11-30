package com.siyeh.ipp.opassign.assignment;

class OperatorAssignment1 {
  void foo(int i) {
    i +<caret>= 4.2;
  }
}