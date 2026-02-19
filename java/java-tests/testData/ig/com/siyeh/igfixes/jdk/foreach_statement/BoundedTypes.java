package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.Collection;

class BoundedTypes {
  void x(Collection<? extends Number> c) {
    <caret>for (Number n : c) {

    }
  }
}
