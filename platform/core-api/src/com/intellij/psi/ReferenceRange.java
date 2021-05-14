// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReferenceRange {
  private ReferenceRange() {
  }

  @NotNull
  public static List<TextRange> getRanges(@NotNull PsiReference ref) {
    if (ref instanceof MultiRangeReference) {
      return ((MultiRangeReference)ref).getRanges();
    }
    return Collections.singletonList(ref.getRangeInElement());
  }

  @NotNull
  public static List<TextRange> getAbsoluteRanges(@NotNull PsiReference ref) {
    final PsiElement elt = ref.getElement();
    final List<TextRange> relativeRanges = getRanges(ref);
    final List<TextRange> answer = new ArrayList<>(relativeRanges.size());
    final int parentOffset = elt.getTextRange().getStartOffset();
    for (TextRange relativeRange : relativeRanges) {
      answer.add(relativeRange.shiftRight(parentOffset));
    }
    return answer;
  }

  public static TextRange getRange(@NotNull PsiReference ref) {
    if (ref instanceof MultiRangeReference) {
      final List<TextRange> ranges = ((MultiRangeReference)ref).getRanges();
      return new TextRange(ranges.get(0).getStartOffset(), ranges.get(ranges.size() - 1).getEndOffset());
    }

    return ref.getRangeInElement();
  }

  public static boolean containsOffsetInElement(@NotNull PsiReference ref, int offset) {
    if (ref instanceof MultiRangeReference) {
      for (TextRange range : ((MultiRangeReference)ref).getRanges()) {
        if (range.containsOffset(offset)) return true;
      }

      return false;
    }
    return ref.getRangeInElement().containsOffset(offset);
  }

  public static boolean containsRangeInElement(@NotNull PsiReference ref, @NotNull TextRange rangeInElement) {
    if (ref instanceof MultiRangeReference) {
      for (TextRange range : ((MultiRangeReference)ref).getRanges()) {
        if (range.contains(rangeInElement)) return true;
      }

      return false;
    }
    return ref.getRangeInElement().contains(rangeInElement);
  }
}
