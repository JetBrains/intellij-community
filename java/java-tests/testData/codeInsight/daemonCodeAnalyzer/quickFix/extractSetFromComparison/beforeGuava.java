// "Extract Set from comparison chain" "true"

package com.google.common.collect;

class ImmutableSet<T> {
  public static <T> ImmutableSet<T> of(T... elements) {
    return null;
  }
}

public class Test {
  void testOr(String s) {
    if("foo"<caret>.equals(s) || "bar".equals(s) || "baz".equals(s)) {
      System.out.println("foobarbaz");
    }
  }
}
