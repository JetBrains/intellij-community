// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public Comparable weigh(@NotNull LookupElement element, @NotNull WeighingContext context) {
    return weigh(element);
  }

  @Nullable
  public Comparable weigh(@NotNull LookupElement element) {
    throw new UnsupportedOperationException("weigh not implemented in " + getClass());
  }

}
