// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class AutoCompletionDecision {
  public static final AutoCompletionDecision SHOW_LOOKUP = new AutoCompletionDecision();
  public static final AutoCompletionDecision CLOSE_LOOKUP = new AutoCompletionDecision();

  public static @NotNull AutoCompletionDecision insertItem(@NotNull LookupElement element) {
    return new InsertItem(element);
  }

  private AutoCompletionDecision() {
  }

  @ApiStatus.Internal
  public static final class InsertItem extends AutoCompletionDecision {
    private final LookupElement myElement;

    private InsertItem(@NotNull LookupElement element) {
      myElement = element;
    }

    public @NotNull LookupElement getElement() {
      return myElement;
    }
  }
}
