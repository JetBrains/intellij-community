// "Fuse newHashSet into the Stream API chain" "true-preview"
package com.google.common.collect;

import java.util.*;
import java.util.stream.*;

class X {
  void foo(Stream<String> s) {
    Set<String> set = Sets.newHashSet(s.co<caret>llect(Collectors.toSet()));
  }
}

class Sets {
  public static native <E> HashSet<E> newHashSet(Iterable<? extends E> var0);
}