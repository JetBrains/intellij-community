// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects modifications to apply to template text for later parsing by template data language parser.
 *
 * @see TemplateDataElementType.RangeCollector
 */
public class TemplateDataModifications {

  public static final TemplateDataModifications EMPTY = new TemplateDataModifications(Collections.emptyList());

  final @NotNull List<TextRange> myOuterAndRemoveRanges;

  public TemplateDataModifications() {
    this(new ArrayList<>());
  }

  private TemplateDataModifications(@NotNull List<TextRange> ranges) {
    myOuterAndRemoveRanges = ranges;
  }

  /**
   * @see TemplateDataElementType.RangeCollector#addOuterRange(TextRange)
   */
  public void addOuterRange(@NotNull TextRange newRange) {
    addOuterRange(newRange, false);
  }

  /**
   * @see TemplateDataElementType.RangeCollector#addOuterRange(TextRange, boolean)
   */
  public void addOuterRange(@NotNull TextRange range, boolean isInsertion) {
    myOuterAndRemoveRanges.add(isInsertion ? new RangeCollectorImpl.InsertionRange(range.getStartOffset(), range.getEndOffset()) : range);
  }

  /**
   * @see TemplateDataElementType.RangeCollector#addRangeToRemove(TextRange)
   */
  public void addRangeToRemove(int startOffset, @NotNull CharSequence textToInsert) {
    myOuterAndRemoveRanges.add(new RangeCollectorImpl.RangeToRemove(startOffset, textToInsert));
  }

  public static @NotNull TemplateDataModifications fromRangeToRemove(int startOffset, @NotNull CharSequence textToInsert) {
    TemplateDataModifications modifications = new TemplateDataModifications();
    modifications.addRangeToRemove(startOffset, textToInsert);
    return modifications;
  }

  public boolean addAll(@NotNull TemplateDataModifications other) {
    return myOuterAndRemoveRanges.addAll(other.myOuterAndRemoveRanges);
  }

  @TestOnly
  public @NotNull Pair<CharSequence, TemplateDataElementType.RangeCollector> applyToText(@NotNull CharSequence text,
                                                                                         @NotNull TemplateDataElementType anyType) {
    RangeCollectorImpl collector = new RangeCollectorImpl(anyType);
    return Pair.create(collector.applyTemplateDataModifications(text, this), collector);
  }
}
