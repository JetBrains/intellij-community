// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LookupElementWeigher {
  private final String myId;
  private final boolean myNegated;
  private final boolean myPrefixDependent;

  protected LookupElementWeigher(@NonNls String id, boolean negated, boolean dependsOnPrefix) {
    myId = id;
    myNegated = negated;
    myPrefixDependent = dependsOnPrefix;
  }

  protected LookupElementWeigher(@NonNls String id) {
    this(id, false, false);
  }

  public boolean isPrefixDependent() {
    return myPrefixDependent;
  }

  public boolean isNegated() {
    return myNegated;
  }

  @Override
  public String toString() {
    return myId;
  }

  public @Nullable Comparable weigh(@NotNull LookupElement element, @NotNull WeighingContext context) {
    return weigh(element);
  }

  public @Nullable Comparable weigh(@NotNull LookupElement element) {
    throw new UnsupportedOperationException("weigh not implemented in " + getClass());
  }

}
