package com.siyeh.ipp.forloop.iterator;

import java.util.ArrayList;
import java.util.Collection;

class Precedence {

  void x(Collection c) {
    <caret>for (Object n : c = new ArrayList()) {

    }
  }

}