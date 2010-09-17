/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  private Comparable[] myComputedWeighs;
  private final T myElement;
  private final Loc myLocation;
  private final Weigher<T,Loc>[] myWeighers;

  public WeighingComparable(final T element,
                            @Nullable final Loc location,
                            final Weigher<T,Loc>[] weighers) {
    myElement = element;
    myLocation = location;
    myWeighers = weighers;
    myComputedWeighs = new Comparable[weighers.length];
  }

  public int compareTo(final WeighingComparable<T,Loc> comparable) {
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
