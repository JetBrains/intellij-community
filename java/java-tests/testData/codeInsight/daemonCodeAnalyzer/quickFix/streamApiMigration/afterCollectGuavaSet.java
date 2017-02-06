// "Replace with toArray" "true"
package com.google.common.collect;

import java.util.*;
import java.util.stream.Stream;

class Sets {
  public static <E extends Comparable> TreeSet<E> newTreeSet() {
    return new TreeSet<E>();
  }
}

public class Test {
  public String[] test(List<String> input) {
      return input.stream().filter(Objects::nonNull).flatMap(s -> Stream.of(s, s + s)).distinct().sorted().toArray(String[]::new);
  }
}
