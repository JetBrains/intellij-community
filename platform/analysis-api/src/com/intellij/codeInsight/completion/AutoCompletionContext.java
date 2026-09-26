// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

public final class AutoCompletionContext {
  private final CompletionParameters myParameters;
  private final LookupElement[] myItems;
  private final OffsetMap myOffsetMap;
  private final Lookup myLookup;

  public AutoCompletionContext(@NotNull CompletionParameters parameters,
                               @NotNull LookupElement @NotNull [] items,
                               @NotNull OffsetMap offsetMap,
                               @NotNull Lookup lookup) {
    myParameters = parameters;
    myItems = items;
    myOffsetMap = offsetMap;
    myLookup = lookup;
  }

  public @NotNull Lookup getLookup() {
    return myLookup;
  }

  public @NotNull CompletionParameters getParameters() {
    return myParameters;
  }

  public @NotNull LookupElement @NotNull [] getItems() {
    return myItems;
  }

  public @NotNull OffsetMap getOffsetMap() {
    return myOffsetMap;
  }
}
