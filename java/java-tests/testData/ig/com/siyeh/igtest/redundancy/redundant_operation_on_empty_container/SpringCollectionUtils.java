package org.springframework.util;

import java.util.*;

class CollectionUtils {
  public static boolean isEmpty(Collection<?> c) {
    return c == null || c.isEmpty();
  }
  public static boolean isEmpty(Map<?, ?> m) {
    return m == null || m.isEmpty();
  }
}

class X {
  void test(List<String> list, Map<String, String> map) {
    if (CollectionUtils.isEmpty(map)) {
      // Unsupported yet, as there's ephemeral state emerged from a null-check
      for(String s:map.keySet()) {
        System.out.println(s);
      }
    }
    if (CollectionUtils.isEmpty(list)) {
      for(String s : <warning descr="Collection 'list' is always empty">list</warning>) {
        System.out.println(s);
      }
    }
  }
}