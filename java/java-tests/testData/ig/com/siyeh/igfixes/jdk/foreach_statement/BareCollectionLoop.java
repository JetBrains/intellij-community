package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.Collection;

class BareCollectionLoop {

  void x(Collection c) {
    fo<caret>r (Object n : c) {

    }
  }
}
