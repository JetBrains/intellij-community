package com.siyeh.ipp.exceptions.detail;

import java.util.List;

class Foreach {

  void foo(List<String> list) {
    <caret>try {
      for (String s : list) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException e) {}
  }
}