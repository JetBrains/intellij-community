package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class RawIterator implements Iterable {

  void m(List<String> ss) {
    final Iterator iterator = ss.iterator();
    while<caret> (iterator.hasNext()) {
      System.out.println(iterator.next());
    }
  }
}
