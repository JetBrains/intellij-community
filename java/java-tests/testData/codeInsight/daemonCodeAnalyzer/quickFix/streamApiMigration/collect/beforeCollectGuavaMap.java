// "Replace with collect" "true"
package com.google.common.collect;

import java.util.*;

class Maps {
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }
}

public class Test {
  public void test() {
    Map<String, Integer> map = Maps.newHashMap();
    for(int <caret>i=0; i<10; i++) {
      if(i%2 == 0) {
        map.put(String.valueOf(i), i);
      }
    }
    System.out.println(map);
  }
}
