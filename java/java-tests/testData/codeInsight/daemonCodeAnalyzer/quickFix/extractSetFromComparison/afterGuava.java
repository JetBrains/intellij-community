// "Extract Set from comparison chain" "true"

package com.google.common.collect;

import java.util.Set;

class ImmutableSet<T> {
  public static <T> ImmutableSet<T> of(T... elements) {
    return null;
  }
}

public class Test {
    private static final Set<String> S = com.google.common.collect.ImmutableSet.of("foo", "bar", "baz");

    void testOr(String s) {
    if(S.contains(s)) {
      System.out.println("foobarbaz");
    }
  }
}
