/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class WeighingComparable<T,Loc> implements Comparable<WeighingComparable<T,Loc>> {
  private static final Comparable NULL = new Comparable() {
    public int compareTo(final Object o) {
      throw new UnsupportedOperationException("Method compareTo is not yet implemented in " + getClass().getName());
    }
  };
  private final Comparable[] myComputedWeighs;
  private final T myElement;
  private final Loc myLocation;
  private final Weigher<T,Loc>[] myWeighers;

  public WeighingComparable(final T element, final Loc location, final Weigher<T,Loc>[] weighers) {
    myElement = element;
    myLocation = location;
    myWeighers = weighers;
    myComputedWeighs = new Comparable[weighers.length];
  }

  public int compareTo(final WeighingComparable<T,Loc> comparable) {
    for (int i = 0; i < myComputedWeighs.length; i++) {
      final Comparable weight1 = getWeight(i);
      final Comparable weight2 = comparable.getWeight(i);
      if (weight1 == null ^ weight2 == null) {
        return weight1 == null ? -1 : 1;
      }

      if (weight1 != null) {
        final int result = weight1.compareTo(weight2);
        if (result != 0) return result;
      }
    }
    return 0;
  }

  @Nullable
  private Comparable getWeight(final int index) {
    Comparable weight = myComputedWeighs[index];
    if (weight == null) {
      weight = myWeighers[index].weigh(myElement, myLocation);
      if (weight == null) weight = NULL;
      myComputedWeighs[index] = weight;
    }
    return weight == NULL ? null : weight;
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < myComputedWeighs.length; i++) {
      if (i != 0) builder.append(", ");
      builder.append(myWeighers[i]);
      builder.append("=");
      builder.append(getWeight(i));
    }
    return builder.append("]").toString();
  }
}
