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

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.util.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SimpleThreesideDiffChange {
  @NotNull private final MergeLineFragment myFragment;
  @NotNull private final List<EditorEx> myEditors;

  @NotNull private ConflictType myType;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  @NotNull private final List<RangeHighlighter> myActionHighlighters = new ArrayList<RangeHighlighter>();

  private boolean myIsValid = true;
  private int[] myLineStartShifts = new int[3];
  private int[] myLineEndShifts = new int[3];

  public SimpleThreesideDiffChange(@NotNull MergeLineFragment fragment,
                                   @NotNull List<EditorEx> editors,
                                   @NotNull ComparisonPolicy policy) {
    myFragment = fragment;
    myEditors = editors;

    myType = calcType(fragment, editors, policy);

    installHighlighter();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    createHighlighter(ThreeSide.BASE);
    if (myType.isLeftChange()) createHighlighter(ThreeSide.LEFT);
    if (myType.isRightChange()) createHighlighter(ThreeSide.RIGHT);

    doInstallActionHighlighters();
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (RangeHighlighter highlighter : myActionHighlighters) {
      highlighter.dispose();
    }
    myActionHighlighters.clear();
  }

  //
  // Highlighting
  //

  private void createHighlighter(@NotNull ThreeSide side) {
    Editor editor = side.selectNotNull(myEditors);
    Document document = editor.getDocument();

    TextDiffType type = getDiffType();
    int startLine = myFragment.getStartLine(side);
    int endLine = myFragment.getEndLine(side);

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

    myHighlighters.add(DiffDrawUtil.createHighlighter(editor, start, end, type));

    if (startLine == endLine) {
      if (startLine != 0) myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, true));
    }
    else {
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, startLine, type, SeparatorPlacement.TOP));
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM));
    }
  }

  private void doInstallActionHighlighters() {
    // TODO
  }

  //
  // Getters
  //

  public int getStartLine(@NotNull ThreeSide side) {
    return myFragment.getStartLine(side) + side.select(myLineStartShifts);
  }

  public int getEndLine(@NotNull ThreeSide side) {
    return myFragment.getEndLine(side) + side.select(myLineEndShifts);
  }

  @NotNull
  public TextDiffType getDiffType() {
    return myType.getDiffType();
  }

  @NotNull
  public ConflictType getType() {
    return myType;
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull ThreeSide side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);

    if (line2 <= oldLine1) return false;
    if (line1 >= oldLine2) {
      myLineStartShifts[side.getIndex()] += shift;
      myLineEndShifts[side.getIndex()] += shift;
      return false;
    }

    if (line1 <= oldLine1 && line2 >= oldLine2) {
      myLineEndShifts[side.getIndex()] += shift;
      return false;
    }

    for (RangeHighlighter highlighter : myActionHighlighters) {
      highlighter.dispose();
    }
    myActionHighlighters.clear();
    myIsValid = false;
    return true;
  }

  //
  // Type
  //

  @NotNull
  private static ConflictType calcType(@NotNull MergeLineFragment fragment,
                                       @NotNull List<EditorEx> editors,
                                       @NotNull ComparisonPolicy policy) {
    if (compareSubstring(fragment, editors, ThreeSide.LEFT, ThreeSide.RIGHT, policy)) {
      return new ConflictType(getDiffType(fragment, ThreeSide.LEFT));
    }
    else if (compareSubstring(fragment, editors, ThreeSide.BASE, ThreeSide.LEFT)) {
      return new ConflictType(getDiffType(fragment, ThreeSide.RIGHT), false, true);
    }
    else if (compareSubstring(fragment, editors, ThreeSide.BASE, ThreeSide.RIGHT)) {
      return new ConflictType(getDiffType(fragment, ThreeSide.LEFT), true, false);
    }
    else {
      return new ConflictType(TextDiffType.CONFLICT);
    }
  }

  private static boolean compareSubstring(@NotNull MergeLineFragment fragment,
                                          @NotNull List<EditorEx> editors,
                                          @NotNull ThreeSide side1,
                                          @NotNull ThreeSide side2) {
    return compareSubstring(fragment, editors, side1, side2, ComparisonPolicy.DEFAULT);
  }

  private static boolean compareSubstring(@NotNull MergeLineFragment fragment,
                                          @NotNull List<EditorEx> editors,
                                          @NotNull ThreeSide side1,
                                          @NotNull ThreeSide side2,
                                          @NotNull ComparisonPolicy policy) {
    CharSequence content1 = getRangeContent(fragment, editors, side1);
    CharSequence content2 = getRangeContent(fragment, editors, side2);

    return ComparisonManager.getInstance().isEquals(content1, content2, policy);
  }

  @NotNull
  private static CharSequence getRangeContent(@NotNull MergeLineFragment fragment,
                                              @NotNull List<EditorEx> editors,
                                              @NotNull ThreeSide side) {
    DocumentEx document = side.selectNotNull(editors).getDocument();
    int line1 = fragment.getStartLine(side);
    int line2 = fragment.getEndLine(side);
    return DiffUtil.getLinesContent(document, line1, line2);
  }

  @NotNull
  private static TextDiffType getDiffType(@NotNull MergeLineFragment fragment, @NotNull ThreeSide side) {
    assert side != ThreeSide.BASE;

    boolean isBaseEmpty = isIntervalEmpty(fragment, ThreeSide.BASE);
    boolean isVersionEmpty = isIntervalEmpty(fragment, side);

    if (!isBaseEmpty && !isVersionEmpty) return TextDiffType.MODIFIED;
    if (!isBaseEmpty) return TextDiffType.DELETED;
    if (!isVersionEmpty) return TextDiffType.INSERTED;
    throw new IllegalArgumentException();
  }

  private static boolean isIntervalEmpty(@NotNull MergeLineFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartLine(side) == fragment.getEndLine(side);
  }

  //
  // Helpers
  //

  public static class ConflictType {
    @NotNull private final TextDiffType myType;
    private final boolean myLeftChange;
    private final boolean myRightChange;

    public ConflictType(@NotNull TextDiffType type) {
      this(type, true, true);
    }

    public ConflictType(@NotNull TextDiffType type, boolean leftChange, boolean rightChange) {
      myType = type;
      myLeftChange = leftChange;
      myRightChange = rightChange;
    }

    @NotNull
    public TextDiffType getDiffType() {
      return myType;
    }

    public boolean isLeftChange() {
      return myLeftChange;
    }

    public boolean isRightChange() {
      return myRightChange;
    }

    public boolean isChange(@NotNull Side side) {
      return side.isLeft() ? myLeftChange : myRightChange;
    }
  }
}