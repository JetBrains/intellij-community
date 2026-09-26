package org.jetbrains.decodeQualifierInMethodReference;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Bar {
  void bar() {
      Function<Map.Entry<Long, Set<Integer>>, Long> getKey = Map.Entry::getKey;

  }
}