package com.siyeh.ipp.bool.negate_conditional;

class Simple {

  void f(boolean z, boolean b) {
      //keep comment
      if (z ? !a(/*inside operand*/) : !b) {

    }

    boolean a() {
      return false;
    }
  }
}