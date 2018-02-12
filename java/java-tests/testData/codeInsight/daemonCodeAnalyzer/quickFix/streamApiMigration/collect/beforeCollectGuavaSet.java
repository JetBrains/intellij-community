// "Replace with toArray" "true"
package com.google.common.collect;

import java.util.*;

class Sets {
  public static <E extends Comparable> TreeSet<E> newTreeSet() {
    return new TreeSet<E>();
  }
}

public class Test {
  public String[] test(List<String> input) {
    Set<String> set = Sets.newTreeSet();
    for(String s : inp<caret>ut) {
      if(s != null) {
        Collections.addAll(set, s, s+s);
      }
    }
    return set.toArray(new String[0]);
  }
}
