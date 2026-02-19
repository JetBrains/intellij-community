package com.siyeh.ipp.exceptions.detail;

class Simple {

  void foo() {
    <caret>try{
      if (true) {
        throw new IllegalArgumentException();
      } else {
        throw new NullPointerException();
      }
    } catch (RuntimeException e) {

    }
  }
}