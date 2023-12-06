package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.Collection;
import java.util.Iterator;

class BareCollectionLoop {

  void x(Collection c) {
      for (Iterator iterator = c.iterator(); iterator.hasNext(); ) {
          Object n = iterator.next();

      }
  }
}
