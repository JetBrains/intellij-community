// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects modifications to apply to template text for later parsing by template data language parser.
 *
 * @see com.intellij.psi.templateLanguages.TemplateDataElementType.RangeCollector
 */
public class TemplateDataModifications {

  final List<TextRange> myOuterAndRemoveRanges = new ArrayList<>();

  public void addOuterRange(@NotNull TextRange newRange) {
    addOuterRange(newRange, false);
  }

  public void addOuterRange(@NotNull TextRange range, boolean isInsertion) {
    myOuterAndRemoveRanges.add(isInsertion ? new RangeCollectorImpl.InsertionRange(range.getStartOffset(), range.getEndOffset()) : range);
  }

  public void addRangeToRemove(int startOffset, @NotNull CharSequence textToInsert) {
    myOuterAndRemoveRanges.add(new RangeCollectorImpl.RangeToRemove(startOffset, textToInsert));
  }

  @TestOnly
  public @NotNull Pair<CharSequence, TemplateDataElementType.RangeCollector> applyToText(@NotNull CharSequence text,
                                                                                         @NotNull TemplateDataElementType anyType) {
    RangeCollectorImpl collector = new RangeCollectorImpl(anyType);
    return Pair.create(collector.applyTemplateDataModifications(text, this), collector);
  }
}
