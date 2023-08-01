package com.siyeh.ipp.opassign.assignment;

class AssignmentAssign {
  void x(Object o, String s) {
    s <caret>+= x instanceof String;
  }
}