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

import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SimpleThreesideDiffChange extends ThreesideDiffChangeBase {
  @NotNull private final List<? extends EditorEx> myEditors;
  @NotNull private final MergeLineFragment myFragment;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  private int[] myLineStartShifts = new int[3];
  private int[] myLineEndShifts = new int[3];

  public SimpleThreesideDiffChange(@NotNull MergeLineFragment fragment,
                                   @NotNull List<? extends EditorEx> editors,
                                   @NotNull ComparisonPolicy policy) {
    super(fragment, editors, policy);
    myEditors = editors;
    myFragment = fragment;

    installHighlighter();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    createHighlighter(ThreeSide.BASE);
    if (getType().isLeftChange()) createHighlighter(ThreeSide.LEFT);
    if (getType().isRightChange()) createHighlighter(ThreeSide.RIGHT);
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
  }

  //
  // Highlighting
  //

  private void createHighlighter(@NotNull ThreeSide side) {
    Editor editor = side.select(myEditors);

    TextDiffType type = getDiffType();
    int startLine = myFragment.getStartLine(side);
    int endLine = myFragment.getEndLine(side);
    boolean hasInner = myFragment.getInnerFragments() != null;

    int start = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine).getStartOffset();

    myHighlighters.addAll(DiffDrawUtil.createHighlighter(editor, startLine, endLine, type, hasInner));
    myHighlighters.addAll(DiffDrawUtil.createLineMarker(editor, startLine, endLine, type, false));

    if (hasInner) {
      for (MergeWordFragment innerFragment : myFragment.getInnerFragments()) {
        int startOffset = innerFragment.getStartOffset(side);
        int endOffset = innerFragment.getEndOffset(side);
        myHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, start + startOffset, start + endOffset, type));
      }
    }
  }

  //
  // Getters
  //

  @Override
  public int getStartLine(@NotNull ThreeSide side) {
    return myFragment.getStartLine(side) + side.select(myLineStartShifts);
  }

  @Override
  public int getEndLine(@NotNull ThreeSide side) {
    return myFragment.getEndLine(side) + side.select(myLineEndShifts);
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull ThreeSide side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);
    int sideIndex = side.getIndex();

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myLineStartShifts[sideIndex] += newRange.startLine - line1;
    myLineEndShifts[sideIndex] += newRange.endLine - line2;

    return newRange.damaged;
  }
}