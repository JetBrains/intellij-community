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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to add custom logic to common comparators. Should be registered under "weigher" extension point with "key" parameter specified.
 * It's almost a must to specify how your weigher relates to the others by priority (see {@link com.intellij.openapi.extensions.LoadingOrder}).
 *
 * Known key values include:
 *  <li> "proximity" to measure proximity level of an element in a particular place (location)
 *  <li> "completion" ({@link com.intellij.codeInsight.completion.CompletionService#RELEVANCE_KEY}) - to compare lookup elements by relevance and move preferred items to the top
 *  <li> "completionSorting" ({@link com.intellij.codeInsight.completion.CompletionService#SORTING_KEY}) - to sort lookup elements across the lookup list
 *
 * Your weigher should return {@link Comparable} instances of the same type.   
 *
 * @author peter
 */
public abstract class Weigher<T, Location> {
  private String myDebugName;

  public void setDebugName(final String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName == null ? super.toString() : myDebugName;
  }

  @Nullable public abstract Comparable weigh(@NotNull T element, @Nullable Location location);
}
