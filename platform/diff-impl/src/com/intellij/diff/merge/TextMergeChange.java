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
package com.intellij.diff.merge;

import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.DiffUtil.UpdatedLineRange;
import com.intellij.diff.util.TextDiffType;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TextMergeChange extends ThreesideDiffChangeBase {
  @NotNull private final TextMergeTool.TextMergeViewer myViewer;
  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  private int[] myStartLines = new int[3];
  private int[] myEndLines = new int[3];
  private boolean myResolved;

  public TextMergeChange(@NotNull MergeLineFragment fragment, @NotNull TextMergeTool.TextMergeViewer viewer) {
    super(fragment, viewer.getViewer().getEditors(), ComparisonPolicy.DEFAULT);
    myViewer = viewer;

    for (ThreeSide side : ThreeSide.values()) {
      myStartLines[side.getIndex()] = fragment.getStartLine(side);
      myEndLines[side.getIndex()] = fragment.getEndLine(side);
    }

    installHighlighter();
  }

  protected void installHighlighter() {
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

  public void reinstallHighlighter() {
    destroyHighlighter();
    installHighlighter();
  }

  private void createHighlighter(@NotNull ThreeSide side) {
    Editor editor = side.select(myViewer.getViewer().getEditors());
    Document document = editor.getDocument();

    TextDiffType type = getDiffType();
    int startLine = getStartLine(side);
    int endLine = getEndLine(side);

    int start;
    int end;
    if (startLine == endLine) {
      start = end = startLine < DiffUtil.getLineCount(document) ? document.getLineStartOffset(startLine) : document.getTextLength();
    }
    else {
      start = document.getLineStartOffset(startLine);
      end = document.getLineEndOffset(endLine - 1);
      if (end < document.getTextLength()) end++;
    }

    if (!isResolved()) myHighlighters.add(DiffDrawUtil.createHighlighter(editor, start, end, type));

    if (startLine == endLine) {
      if (startLine != 0) {
        myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, true));
      }
    }
    else {
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, startLine, type, SeparatorPlacement.TOP, false));
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, false));
    }
  }

  //
  // Getters
  //

  public void markResolved() {
    myResolved = true;
    reinstallHighlighter();
  }

  public boolean isResolved() {
    return myResolved;
  }

  public int getStartLine(@NotNull ThreeSide side) {
    return side.select(myStartLines);
  }

  public int getEndLine(@NotNull ThreeSide side) {
    return side.select(myEndLines);
  }

  //
  // Shift
  //

  public boolean processBaseChange(int oldLine1, int oldLine2, int shift) {
    int line1 = getStartLine(ThreeSide.BASE);
    int line2 = getEndLine(ThreeSide.BASE);
    int baseIndex = ThreeSide.BASE.getIndex();

    UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myStartLines[baseIndex] = newRange.startLine;
    myEndLines[baseIndex] = newRange.endLine;

    boolean rangeAffected = oldLine2 >= line1 && oldLine1 <= line2; // RangeMarker can be updated in a different way
    return newRange.damaged || rangeAffected;
  }
}
