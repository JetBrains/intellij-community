package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.ArrayList;
import java.util.Collection;

class Precedence {

  void x(Collection c) {
    <caret>for (Object n : c = new ArrayList()) {

    }
  }

}