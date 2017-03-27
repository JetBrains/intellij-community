/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch;

import org.jetbrains.annotations.NotNull;

public class OccurrencesAware<V> implements Comparable<OccurrencesAware<V>> {
  private final V myUnderlying;
  private final int myOccurrences;

  public OccurrencesAware(final V underlying, final int occurrences) {
    myUnderlying = underlying;
    myOccurrences = occurrences;
  }

  public V getUnderlying() {
    return myUnderlying;
  }

  public int getOccurrences() {
    return myOccurrences;
  }

  @Override
  public int compareTo(@NotNull final OccurrencesAware<V> that) {
    final int sub = -getOccurrences() + that.getOccurrences();
    if (sub != 0) {
      return sub;
    }
    return myUnderlying.hashCode() - that.myUnderlying.hashCode();
  }

  @Override
  public String toString() {
    return getOccurrences() + " for " + myUnderlying;
  }
}
