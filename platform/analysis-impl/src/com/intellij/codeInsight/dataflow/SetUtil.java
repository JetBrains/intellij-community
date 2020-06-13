// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.dataflow;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: oleg
 */
public final class SetUtil {
  private SetUtil() {
  }

  /**
   * Intersects two sets
   */
  public static <T> Set<T> intersect(final Set<? extends T> set1, final Set<? extends T> set2) {
    if (set1.equals(set2)){
      return (Set<T>)set1;
    }
    Set<T> result = new HashSet<>();
    Set<? extends T> minSet;
    Set<? extends T> otherSet;
    if (set1.size() < set2.size()){
      minSet = set1;
      otherSet = set2;
    } else {
      minSet = set2;
      otherSet = set1;
    }
    for (T s : minSet) {
      if (otherSet.contains(s)){
        result.add(s);
      }
    }
    return result;
  }
}
