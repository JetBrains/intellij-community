// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public final class FormatTextRange {
  private @NotNull TextRange formattingRange;
  private final boolean processHeadingWhitespace;

  public FormatTextRange(@NotNull TextRange range, boolean processHeadingSpace) {
    formattingRange = range;
    processHeadingWhitespace = processHeadingSpace;
  }
  
  public boolean isProcessHeadingWhitespace() {
    return processHeadingWhitespace;
  }

  public boolean isWhitespaceReadOnly(@NotNull TextRange range) {
    if (range.getStartOffset() >= formattingRange.getEndOffset()) return true;
    
    if (processHeadingWhitespace && range.getEndOffset() == formattingRange.getStartOffset()) {
      return false;
    }
    
    return range.getEndOffset() <= formattingRange.getStartOffset();
  }

  public int getStartOffset() {
    return formattingRange.getStartOffset();
  }

  public boolean isReadOnly(@NotNull TextRange range) {
    return range.getStartOffset() > formattingRange.getEndOffset() || range.getEndOffset() < formattingRange.getStartOffset();
  }

  public @NotNull TextRange getTextRange() {
    return formattingRange;
  }

  public void setTextRange(@NotNull TextRange range) {
    formattingRange = range;
  }

  public TextRange getNonEmptyTextRange() {
    int endOffset = formattingRange.getStartOffset() == formattingRange.getEndOffset()
                 ? formattingRange.getEndOffset() + 1
                 : formattingRange.getEndOffset();
    
    return new TextRange(formattingRange.getStartOffset(), endOffset);
  }

  @Override
  public String toString() {
    return formattingRange.toString() + (processHeadingWhitespace ? "+" : "");
  }
}