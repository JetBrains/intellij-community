// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
final class PersistentRangeHighlighterImpl extends RangeHighlighterImpl {
  // temporary fields, to investigate exception
  short prevStartOffset;
  short prevEndOffset;
  byte modificationStamp;

  static @NotNull PersistentRangeHighlighterImpl create(@NotNull MarkupModelImpl model,
                                                        int offset,
                                                        int layer,
                                                        @NotNull HighlighterTargetArea target,
                                                        @Nullable TextAttributesKey textAttributesKey,
                                                        boolean normalizeStartOffset) {
    int line = model.getDocument().getLineNumber(offset);
    int startOffset = normalizeStartOffset ? model.getDocument().getLineStartOffset(line) : offset;
    int endOffset = model.getDocument().getLineEndOffset(line);
    return new PersistentRangeHighlighterImpl(model, startOffset, endOffset, layer, target, textAttributesKey);
  }

  private PersistentRangeHighlighterImpl(@NotNull MarkupModelImpl model,
                                         int startOffset,
                                         int endOffset,
                                         int layer,
                                         @NotNull HighlighterTargetArea target,
                                         @Nullable TextAttributesKey textAttributesKey) {
    super(model, startOffset, endOffset, layer, target, textAttributesKey, false, false);
  }

  @Override
  public boolean isPersistent() {
    return true;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    prevStartOffset = (short)intervalStart();
    prevEndOffset = (short)intervalEnd();
    modificationStamp = (byte)e.getDocument().getModificationStamp();
    persistentHighlighterUpdate(e, getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE);
  }

  @Override
  @NonNls
  public String toString() {
    return "PersistentRangeHighlighter" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," +
           (getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE ? "whole-line" : "exact") + "," +
           getStartOffset() + "," + getEndOffset() +
           (isGreedyToRight() ? "]" : ")");
  }
}
