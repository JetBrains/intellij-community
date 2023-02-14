// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class WeighingComparable<T,Loc> implements Comparable<WeighingComparable<T,Loc>>, ForceableComparable {
  private static final Comparable NULL = new Comparable() {
    @Override
    public int compareTo(final Object o) {
      throw new UnsupportedOperationException("Method compareTo is not yet implemented in " + getClass().getName());
    }

    @Override
    public String toString() {
      return "null";
    }
  };
  private Comparable @NotNull [] myComputedWeighs;
  private final Computable<? extends T> myElement;
  private final Loc myLocation;
  private final Weigher<T,Loc>[] myWeighers;

  public WeighingComparable(final Computable<? extends T> element,
                            @Nullable final Loc location,
                            final Weigher<T,Loc>[] weighers) {
    myElement = element;
    myLocation = location;
    myWeighers = weighers;
    myComputedWeighs = new Comparable[weighers.length];
  }

  @Override
  public void force() {
    for (int i = 0; i < myComputedWeighs.length; i++) {
      Comparable weight = getWeight(i);
      if (weight instanceof ForceableComparable) {
        ((ForceableComparable)weight).force();
      }
    }
  }

  @Override
  public int compareTo(@NotNull final WeighingComparable<T,Loc> comparable) {
    if (myComputedWeighs == comparable.myComputedWeighs) return 0;

    for (int i = 0; i < myComputedWeighs.length; i++) {
      final Comparable weight1 = getWeight(i);
      final Comparable weight2 = comparable.getWeight(i);
      if (weight1 == null ^ weight2 == null) {
        return weight1 == null ? -1 : 1;
      }

      if (weight1 != null) {
        final int result = weight1.compareTo(weight2);
        if (result != 0) {
          return result;
        }
      }
    }
    myComputedWeighs = comparable.myComputedWeighs;
    return 0;
  }

  @Nullable
  private Comparable getWeight(final int index) {
    Comparable weight = myComputedWeighs[index];
    if (weight == null) {
      T element = myElement.compute();
      weight = element == null ? NULL : myWeighers[index].weigh(element, myLocation);
      if (weight == null) weight = NULL;
      myComputedWeighs[index] = weight;
    }
    return weight == NULL ? null : weight;
  }

  @ApiStatus.Internal
  public Map<String, Object> getWeights() {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < myComputedWeighs.length; i++) {
      if (myComputedWeighs[i] == NULL) continue;

      result.put(myWeighers[i].toString(), myComputedWeighs[i]);
    }
    return result;
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < myComputedWeighs.length; i++) {
      if (i != 0) builder.append(", ");
      builder.append(myWeighers[i]);
      builder.append("=");
      builder.append(myComputedWeighs[i]);
    }
    return builder.append("]").toString();
  }
}
