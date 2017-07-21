// "Replace with collect" "true"
package com.google.common.collect;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Maps {
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }
}

public class Test {
  public void test() {
      Map<String, Integer> map = IntStream.range(0, 10).filter(i -> i % 2 == 0).collect(Collectors.toMap(String::valueOf, i -> i, (a, b) -> b));
      System.out.println(map);
  }
}
