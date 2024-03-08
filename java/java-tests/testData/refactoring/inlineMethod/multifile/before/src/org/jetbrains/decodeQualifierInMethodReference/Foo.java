package org.jetbrains.decodeQualifierInMethodReference;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class Foo {
  public static void foo() {

    Function<Map.Entry<Long, Set<Integer>>, Long> getKey = Map.Entry::getKey;

  }
}