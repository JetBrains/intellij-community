/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @NotNull private final MergeConflictType myType;

  @NotNull protected final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull protected final List<RangeHighlighter> myInnerHighlighters = new ArrayList<>();
  @NotNull protected final List<DiffGutterOperation> myOperations = new ArrayList<>();

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

  @NotNull
  protected abstract Editor getEditor(@NotNull ThreeSide side);

  @Nullable
  protected abstract MergeInnerDifferences getInnerFragments();

  @NotNull
  public TextDiffType getDiffType() {
    return DiffUtil.getDiffType(myType);
  }

  @NotNull
  public MergeConflictType getConflictType() {
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