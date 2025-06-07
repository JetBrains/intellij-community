// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.TextRangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class FormatTextRanges implements FormattingRangesInfo {
  private final List<TextRange>       myInsertedRanges;
  private final List<FormatTextRange> myRanges         = new ArrayList<>();
  private final List<TextRange>       myExtendedRanges = new ArrayList<>();
  private final List<TextRange>       myDisabledRanges = new ArrayList<>();

  private boolean myExtendToContext;

  public FormatTextRanges() {
    myInsertedRanges = null;
  }

  public FormatTextRanges(TextRange range, boolean processHeadingWhitespace) {
    myInsertedRanges = null;
    add(range, processHeadingWhitespace);
  }

  public FormatTextRanges(@NotNull ChangedRangesInfo changedRangesInfo, @NotNull List<? extends TextRange> contextRanges) {
    myInsertedRanges = changedRangesInfo.insertedRanges;
    boolean processHeadingWhitespace = false;
    for (TextRange range : contextRanges) {
      add(range, processHeadingWhitespace);
      processHeadingWhitespace = true;
    }
  }

  public void add(TextRange range, boolean processHeadingWhitespace) {
    myRanges.add(new FormatTextRange(range, processHeadingWhitespace));
  }

  @Override
  public boolean isWhitespaceReadOnly(final @NotNull TextRange range) {
    return ContainerUtil.and(myRanges, formatTextRange -> formatTextRange.isWhitespaceReadOnly(range));
  }
  
  @Override
  public boolean isReadOnly(@NotNull TextRange range) {
    return ContainerUtil.and(myRanges, formatTextRange -> formatTextRange.isReadOnly(range));
  }

  @Override
  public boolean isOnInsertedLine(int offset) {
    if (myInsertedRanges == null) return false;

    Optional<TextRange> enclosingRange = myInsertedRanges.stream()
      .filter((range) -> range.contains(offset))
      .findAny();

    return enclosingRange.isPresent();
  }
  
  public List<FormatTextRange> getRanges() {
    return myRanges;
  }

  public FormatTextRanges ensureNonEmpty() {
    FormatTextRanges result = new FormatTextRanges();
    for (FormatTextRange range : myRanges) {
      if (range.isProcessHeadingWhitespace()) {
        result.add(range.getNonEmptyTextRange(), true);
      }
      else {
        result.add(range.getTextRange(), false);
      }
    }
    return result;
  }

  public boolean isEmpty() {
    return myRanges.isEmpty();
  }

  public boolean isFullReformat(PsiFile file) {
    return myRanges.size() == 1 && file.getTextRange().equals(myRanges.get(0).getTextRange());
  }

  @Override
  public @Unmodifiable @NotNull List<TextRange> getTextRanges() {
    return ContainerUtil.sorted(ContainerUtil.map(myRanges, FormatTextRange::getTextRange), Segment.BY_START_OFFSET_THEN_END_OFFSET);
  }

  public void setExtendedRanges(@NotNull List<? extends TextRange> extendedRanges) {
    myExtendedRanges.addAll(extendedRanges);
  }
  
  public List<TextRange> getExtendedRanges() {
    return myExtendedRanges.isEmpty() ? getTextRanges() : myExtendedRanges;
  }

  @Override
  public @Nullable TextRange getBoundRange() {
    List<TextRange> ranges = getTextRanges();
    return !ranges.isEmpty() ?
           new TextRange(ranges.get(0).getStartOffset(), ranges.get(ranges.size() - 1).getEndOffset()) :
           null;
  }

  public boolean isExtendToContext() {
    return myExtendToContext;
  }

  public void setExtendToContext(boolean extendToContext) {
    myExtendToContext = extendToContext;
  }

  public void setDisabledRanges(@NotNull Collection<? extends TextRange> disabledRanges) {
    myDisabledRanges.clear();
    myDisabledRanges.addAll(ContainerUtil.sorted(disabledRanges, Segment.BY_START_OFFSET_THEN_END_OFFSET));
  }

  public boolean isInDisabledRange(@NotNull TextRange textRange) {
    return TextRangeUtil.intersectsOneOf(textRange, myDisabledRanges);
  }
}