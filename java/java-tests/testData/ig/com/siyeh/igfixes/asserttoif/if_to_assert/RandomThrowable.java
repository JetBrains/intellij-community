package com.siyeh.ipp.asserttoif.if_to_assert;

class RandomThrowable {
  void m(Object o) {
    <caret>if (o == null) {
      throw new NullPointerException("wtf?");
    }
  }
}