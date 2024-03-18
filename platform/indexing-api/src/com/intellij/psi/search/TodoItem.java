// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public interface TodoItem {
  @NotNull
  PsiFile getFile();

  @NotNull
  TextRange getTextRange();

  /**
   * Returns a {@link TodoPattern} that matches this item. A pattern may be missing if an item does not match any patterns, as can be the
   * case when an item is created for a language's built-in syntax instead of from an occurrence in a comment.
   */
  @Nullable
  TodoPattern getPattern();

  @NotNull
  default List<TextRange> getAdditionalTextRanges() {
    return Collections.emptyList();
  }

  Comparator<TodoItem> BY_START_OFFSET = Comparator.comparingInt(o -> o.getTextRange().getStartOffset());
}
