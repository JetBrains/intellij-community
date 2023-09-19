// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FilterMixin {
  boolean shouldRunHeavy();
  void applyHeavyFilter(@NotNull Document copiedFragment, int startOffset, int startLineNumber, @NotNull Consumer<? super AdditionalHighlight> consumer);

  @NotNull
  @Nls
  String getUpdateMessage();

  class AdditionalHighlight extends Filter.Result {
    public AdditionalHighlight(int start, int end) {
      super(start, end, null);
    }

    public AdditionalHighlight(@NotNull List<? extends Filter.ResultItem> resultItems) {
      super(resultItems);
    }

    public @Nullable TextAttributes getTextAttributes(final @Nullable TextAttributes source) {
      return null;
    }
  }
}
