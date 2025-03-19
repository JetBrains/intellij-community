package org.jetbrains.multiModuleDependencies;


import org.jetbrains.api.InnerFunction;

import java.util.Map;
import java.util.Set;

class Foo {
  public static void foo() {
    InnerFunction<Map.Entry<Long, Set<Integer>>, Long> getKey = Map.Entry::getKey;

  }
}