package com.siyeh.igfixes.jdk.foreach_statement;

import java.util.List;
import java.util.Map;

class Wildcards {

  void renames(Map<? extends List, String> allRenames) {
    for<caret> (Map.Entry<? extends List, String> entry : allRenames.entrySet()) {
    }
  }
}