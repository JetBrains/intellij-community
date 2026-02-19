package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class Cast {
  void m(List ss) {
    final Iterator<String> iterator = ss.iterator();
    while<caret> (iterator.hasNext()) {
      System.out.println(iterator.next());
    }
  }
}
