// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

@SuppressWarnings("SameParameterValue")
final
class LineFormattingRangesExtender {
  private static final Logger LOG = Logger.getInstance(ContextFormattingRangesExtender.class);

  private final Document myDocument;

  LineFormattingRangesExtender(@NotNull Document document) {
    myDocument = document;
  }

  public @Unmodifiable List<TextRange> getExtendedRanges(@NotNull List<? extends TextRange> ranges) {
    return ContainerUtil.map(ranges, range -> processRange(range));
  }

  private TextRange processRange(@NotNull TextRange originalRange) {
    TextRange validRange = ensureRangeIsValid(originalRange);
    if (!validRange.isEmpty()) {
      return expandToLines(validRange);
    }
    return validRange;
  }

  private TextRange expandToLines(@NotNull TextRange original) {
    int startLine = myDocument.getLineNumber(original.getStartOffset());
    int endLine = myDocument.getLineNumber(original.getEndOffset() - 1);
    int rangeStart = myDocument.getLineStartOffset(startLine);
    int rangeEnd = myDocument.getLineEndOffset(endLine);
    if (rangeStart > 0 && myDocument.getCharsSequence().charAt(rangeStart - 1) == '\n') rangeStart--;
    return new TextRange(rangeStart, rangeEnd);
  }

  private TextRange ensureRangeIsValid(@NotNull TextRange range) {
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    final int docLength = myDocument.getTextLength();
    if (endOffset > docLength) {
      LOG.warn("The given range " + endOffset + " exceeds the document length " + docLength);
      return new TextRange(Math.min(startOffset, docLength), docLength);
    }
    return range;
  }

}