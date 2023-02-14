// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to add custom logic to common comparators. Should be registered under {@code com.intellij.weigher} extension point with {@code key} parameter specified.
 * It's almost a must to specify how your weigher relates to the others by priority (see {@link com.intellij.openapi.extensions.LoadingOrder}).
 * <p>
 * Known key values include:
 * <li> "proximity" to measure proximity level of an element in a particular place (location)
 * <li> "completion" ({@link com.intellij.codeInsight.completion.CompletionService#RELEVANCE_KEY}) - to compare lookup elements by relevance and move preferred items to the top
 * <p>
 * Your weigher should return {@link Comparable} instances of the same type.
 */
public abstract class Weigher<T, Location> {
  private String myDebugName;

  public void setDebugName(final String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName == null ? super.toString() : myDebugName;
  }

  @Nullable
  public abstract Comparable weigh(@NotNull T element, @NotNull Location location);
}
