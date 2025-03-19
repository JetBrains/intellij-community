package com.siyeh.ipp.opassign.assignment;

class AssignmentAssign {
  void x(int i) {
      i = i + (i = 3);
  }
}