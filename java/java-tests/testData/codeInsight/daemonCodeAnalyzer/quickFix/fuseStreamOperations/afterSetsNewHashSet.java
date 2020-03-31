// "Fuse newHashSet into the Stream API chain" "true"
package com.google.common.collect;

import java.util.*;
import java.util.stream.*;

class X {
  void foo(Stream<String> s) {
    Set<String> set = s.collect(Collectors.toSet());
  }
}

class Sets {
  public static native <E> HashSet<E> newHashSet(Iterable<? extends E> var0);
}