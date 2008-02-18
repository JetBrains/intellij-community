/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class WeighingComparable<T,Loc> implements Comparable<WeighingComparable<T,Loc>> {
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
    assert myWeighers == comparable.myWeighers;

    for (int i = 0; i < myComputedWeighs.length; i++) {
      final int result = getWeight(i).compareTo(comparable.getWeight(i));
      if (result != 0) return result;
    }
    return 0;
  }

  @NotNull
  private Comparable getWeight(final int index) {
    Comparable weight = myComputedWeighs[index];
    if (weight == null) {
      weight = myWeighers[index].weigh(myElement, myLocation);
      if (weight == null) weight = 0;
      myComputedWeighs[index] = weight;
    }
    return weight;
  }
}
