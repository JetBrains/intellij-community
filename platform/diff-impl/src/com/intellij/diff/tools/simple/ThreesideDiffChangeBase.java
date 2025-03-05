// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple;

import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.util.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideDiffChangeBase {
  protected @NotNull MergeConflictType myType;

  protected final @NotNull List<RangeHighlighter> myHighlighters = new ArrayList<>();
  protected final @NotNull List<RangeHighlighter> myInnerHighlighters = new ArrayList<>();
  protected final @NotNull List<DiffGutterOperation> myOperations = new ArrayList<>();

  public ThreesideDiffChangeBase(@NotNull MergeConflictType type) {
    myType = type;
  }

  @RequiresEdt
  public void destroy() {
    destroyHighlighters();
    destroyInnerHighlighters();
    destroyOperations();
  }

  @RequiresEdt
  protected void installHighlighters() {
    assert myHighlighters.isEmpty();

    createHighlighter(ThreeSide.BASE);
    if (isChange(Side.LEFT)) createHighlighter(ThreeSide.LEFT);
    if (isChange(Side.RIGHT)) createHighlighter(ThreeSide.RIGHT);
  }

  @RequiresEdt
  protected void installInnerHighlighters() {
    assert myInnerHighlighters.isEmpty();

    createInnerHighlighter(ThreeSide.BASE);
    if (isChange(Side.LEFT)) createInnerHighlighter(ThreeSide.LEFT);
    if (isChange(Side.RIGHT)) createInnerHighlighter(ThreeSide.RIGHT);
  }

  @RequiresEdt
  protected void destroyHighlighters() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
  }

  @RequiresEdt
  protected void destroyInnerHighlighters() {
    for (RangeHighlighter highlighter : myInnerHighlighters) {
      highlighter.dispose();
    }
    myInnerHighlighters.clear();
  }

  @RequiresEdt
  protected void installOperations() {
  }

  @RequiresEdt
  protected void destroyOperations() {
    for (DiffGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  public void updateGutterActions(boolean force) {
    for (DiffGutterOperation operation : myOperations) {
      operation.update(force);
    }
  }

  //
  // Getters
  //

  public abstract int getStartLine(@NotNull ThreeSide side);

  public abstract int getEndLine(@NotNull ThreeSide side);

  public abstract boolean isResolved(@NotNull ThreeSide side);

  protected abstract @NotNull Editor getEditor(@NotNull ThreeSide side);

  protected abstract @Nullable MergeInnerDifferences getInnerFragments();

  public @NotNull TextDiffType getDiffType() {
    return DiffUtil.getDiffType(myType);
  }

  public @NotNull MergeConflictType getConflictType() {
    return myType;
  }

  public boolean isConflict() {
    return myType.getType() == MergeConflictType.Type.CONFLICT;
  }

  public boolean isChange(@NotNull Side side) {
    return myType.isChange(side);
  }

  public boolean isChange(@NotNull ThreeSide side) {
    return myType.isChange(side);
  }

  //
  // Highlighters
  //

  protected void createHighlighter(@NotNull ThreeSide side) {
    Editor editor = getEditor(side);

    TextDiffType type = getDiffType();
    int startLine = getStartLine(side);
    int endLine = getEndLine(side);

    boolean resolved = isResolved(side);
    boolean ignored = !resolved && getInnerFragments() != null;
    boolean shouldHideWithoutLineNumbers = side == ThreeSide.BASE && !isChange(Side.LEFT) && isChange(Side.RIGHT);
    myHighlighters.addAll(new DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, type)
                            .withIgnored(ignored)
                            .withResolved(resolved)
                            .withHideWithoutLineNumbers(shouldHideWithoutLineNumbers)
                            .withHideStripeMarkers(side == ThreeSide.BASE)
                            .done());
  }

  protected void createInnerHighlighter(@NotNull ThreeSide side) {
    if (isResolved(side)) return;
    MergeInnerDifferences innerFragments = getInnerFragments();
    if (innerFragments == null) return;

    List<TextRange> ranges = innerFragments.get(side);
    if (ranges == null) return;

    Editor editor = getEditor(side);
    int start = DiffUtil.getLinesRange(editor.getDocument(), getStartLine(side), getEndLine(side)).getStartOffset();
    for (TextRange fragment : ranges) {
      int innerStart = start + fragment.getStartOffset();
      int innerEnd = start + fragment.getEndOffset();
      myInnerHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, getDiffType()));
    }
  }
}