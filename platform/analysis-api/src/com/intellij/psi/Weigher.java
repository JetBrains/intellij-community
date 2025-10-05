// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows adding custom logic to common comparators. Should be registered under {@code com.intellij.weigher} extension point with {@code key} parameter specified.
 * It's almost a must to specify how your weigher relates to the others by priority (see {@link com.intellij.openapi.extensions.LoadingOrder}).
 * <p>
 * Known key values include:
 * <li> "proximity" to measure the proximity level of an element in a particular place (location)
 * <li> "completion" ({@link com.intellij.codeInsight.completion.CompletionService#RELEVANCE_KEY}) - to compare lookup elements by relevance and move preferred items to the top
 * <p>
 * Your weigher should return {@link Comparable} instances of the same type.
 */
public abstract class Weigher<T, Location> {
  private String myDebugName;

  public void setDebugName(final String debugName) {
    myDebugName = debugName;
  }

  @Override
  public String toString() {
    return myDebugName == null ? super.toString() : myDebugName;
  }

  public abstract @Nullable Comparable weigh(@NotNull T element, @NotNull Location location);
}
