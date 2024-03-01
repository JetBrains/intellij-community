package org.jetbrains.multiModuleDependencies;

import org.jetbrains.api.InnerFunction;

import java.util.Map;
import java.util.Set;

public class Bar {
  void bar() {
      InnerFunction<Map.Entry<Long, Set<Integer>>, Long> getKey = Map.Entry::getKey;

  }
}