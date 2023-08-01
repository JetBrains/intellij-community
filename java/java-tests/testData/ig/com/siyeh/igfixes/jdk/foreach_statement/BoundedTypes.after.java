package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.Collection;
import java.util.Iterator;

class BoundedTypes {
  void x(Collection<? extends Number> c) {
      for (Iterator<? extends Number> iterator = c.iterator(); iterator.hasNext(); ) {
          Number n = iterator.next();

      }
  }
}
