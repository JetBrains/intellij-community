// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

public final class ExplicitProximityWeigher extends CompletionWeigher {

  @Override
  public Integer weigh(final @NotNull LookupElement item, final @NotNull CompletionLocation location) {
    PrioritizedLookupElement<?> prioritized = item.as(PrioritizedLookupElement.CLASS_CONDITION_KEY);
    return prioritized != null ? prioritized.getExplicitProximity() : 0;
  }
}
