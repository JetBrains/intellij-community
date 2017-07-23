// "Replace with collect" "true"
package com.google.common.collect;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Lists {
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<E>();
  }
}

public class Test {
  public void test() {
      List<String> list = IntStream.range(0, 10).filter(i -> i % 2 == 0).mapToObj(String::valueOf).collect(Collectors.toList());
      System.out.println(list);
  }
}
