package com.siyeh.ipp.opassign.assignment;

class Precedence {

  void foo() {
      int a = Integer.MAX_VALUE;
      double d = Double.MAX_VALUE;
      a<caret> += d - d;// a == 2147483647;
  }
}