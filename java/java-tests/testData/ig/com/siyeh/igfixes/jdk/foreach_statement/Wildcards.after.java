package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

class Wildcards {

  void renames(Map<? extends List, String> allRenames) {
      for (Iterator<? extends Map.Entry<? extends List, String>> iterator = allRenames.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<? extends List, String> entry = iterator.next();
      }
  }
}