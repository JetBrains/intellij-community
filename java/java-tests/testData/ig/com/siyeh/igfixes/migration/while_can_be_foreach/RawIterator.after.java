package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class RawIterator implements Iterable {

  void m(List<String> ss) {
      for (String s : ss) {
          System.out.println(s);
      }
  }
}
