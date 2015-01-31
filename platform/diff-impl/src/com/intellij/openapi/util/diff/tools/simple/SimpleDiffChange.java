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
package com.intellij.openapi.util.diff.tools.simple;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.fragments.DiffFragment;
import com.intellij.openapi.util.diff.fragments.FineLineFragment;
import com.intellij.openapi.util.diff.fragments.LineFragment;
import com.intellij.openapi.util.diff.util.*;
import org.intellij.lang.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class SimpleDiffChange {
  @NotNull private final LineFragment myFragment;
  @Nullable private final List<DiffFragment> myFineFragments;

  @Nullable private final EditorEx myEditor1;
  @Nullable private final EditorEx myEditor2;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  @NotNull private final List<RangeHighlighter> myActionHighlighters = new ArrayList<RangeHighlighter>();

  private boolean myIsValid = true;
  private int myLineShift1;
  private int myLineShift2;

  // TODO: adjust color from inner fragments - configurable
  public SimpleDiffChange(@NotNull LineFragment fragment,
                          @Nullable EditorEx editor1,
                          @Nullable EditorEx editor2,
                          boolean inlineHighlight) {
    myFragment = fragment;
    myFineFragments = inlineHighlight && fragment instanceof FineLineFragment ? ((FineLineFragment)fragment).getFineFragments() : null;

    myEditor1 = editor1;
    myEditor2 = editor2;

    installHighlighter();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    if (myFineFragments != null) {
      doInstallHighlighterWithInner();
    }
    else {
      doInstallHighlighterSimple();
    }
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

  private void doInstallHighlighterSimple() {
    createHighlighter(Side.LEFT, false);
    createHighlighter(Side.RIGHT, false);
  }

  private void doInstallHighlighterWithInner() {
    assert myFineFragments != null;

    createHighlighter(Side.LEFT, true);
    createHighlighter(Side.RIGHT, true);

    for (DiffFragment fragment : myFineFragments) {
      createInlineHighlighter(fragment, Side.LEFT);
      createInlineHighlighter(fragment, Side.RIGHT);
    }
  }

  private void doInstallActionHighlighters() {
    if (myEditor1 != null && myEditor2 != null) {
      if (DiffUtil.isEditable(myEditor1)) {
        MyReplaceOperation operation = new MyReplaceOperation(Side.LEFT);
        myActionHighlighters.add(DiffOperation.createHighlighter(myEditor2, operation, myFragment.getStartOffset2()));
      }
      if (DiffUtil.isEditable(myEditor2)) {
        MyReplaceOperation operation = new MyReplaceOperation(Side.RIGHT);
        myActionHighlighters.add(DiffOperation.createHighlighter(myEditor1, operation, myFragment.getStartOffset1()));
      }
    }
  }

  private void createHighlighter(@NotNull Side side, boolean ignored) {
    Editor editor = side.select(myEditor1, myEditor2);
    if (editor == null) return;

    int start = side.getStartOffset(myFragment);
    int end = side.getEndOffset(myFragment);
    TextDiffType type = DiffUtil.getLineDiffType(myFragment);

    myHighlighters.add(DiffDrawUtil.createHighlighter(editor, start, end, type, ignored));

    int startLine = side.getStartLine(myFragment);
    int endLine = side.getEndLine(myFragment);

    if (startLine == endLine) {
      if (startLine != 0) myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, true));
    }
    else {
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, startLine, type, SeparatorPlacement.TOP));
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM));
    }
  }

  private void createInlineHighlighter(@NotNull DiffFragment fragment, @NotNull Side side) {
    Editor editor = side.select(myEditor1, myEditor2);
    if (editor == null) return;

    int start = side.getStartOffset(fragment);
    int end = side.getEndOffset(fragment);
    TextDiffType type = DiffUtil.getDiffType(fragment);

    int startOffset = side.getStartOffset(myFragment);
    start += startOffset;
    end += startOffset;

    RangeHighlighter highlighter = DiffDrawUtil.createInlineHighlighter(editor, start, end, type);
    myHighlighters.add(highlighter);
  }

  //
  // Getters
  //

  public int getStartLine(@NotNull Side side) {
    return side.getStartLine(myFragment) + getShift(side);
  }

  public int getEndLine(@NotNull Side side) {
    return side.getEndLine(myFragment) + getShift(side);
  }

  @NotNull
  public TextDiffType getDiffType() {
    return DiffUtil.getLineDiffType(myFragment);
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull Side side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);

    if (line2 > oldLine1 && line1 < oldLine2) {
      for (RangeHighlighter highlighter : myActionHighlighters) {
        highlighter.dispose();
      }
      myActionHighlighters.clear();
      myIsValid = false;
      return true;
    }

    if (oldLine2 <= getStartLine(side)) {
      shift(side, shift);
    }
    return false;
  }

  private void shift(@NotNull Side side, int shift) {
    if (side.isLeft()) {
      myLineShift1 += shift;
    }
    else {
      myLineShift2 += shift;
    }
  }

  private int getShift(@NotNull Side side) {
    return side.isLeft() ? myLineShift1 : myLineShift2;
  }

  //
  // Change applying
  //

  public boolean isSelectedByLine(int line, @NotNull Side side) {
    if (myEditor1 == null || myEditor2 == null) return false;

    int line1 = getStartLine(side);
    int line2 = getEndLine(side);

    return DiffUtil.isSelectedByLine(line, line1, line2);
  }

  @CalledWithWriteLock
  public void replaceChange(@NotNull final Side sourceSide) {
    assert myEditor1 != null && myEditor2 != null;

    if (!myIsValid) return;

    final Document document1 = myEditor1.getDocument();
    final Document document2 = myEditor2.getDocument();

    DiffUtil.applyModification(sourceSide.other().selectN(document1, document2),
                               getStartLine(sourceSide.other()), getEndLine(sourceSide.other()),
                               sourceSide.selectN(document1, document2),
                               getStartLine(sourceSide), getEndLine(sourceSide));

    destroyHighlighter();
  }

  @CalledWithWriteLock
  public void appendChange(@NotNull final Side sourceSide) {
    assert myEditor1 != null && myEditor2 != null;

    if (!myIsValid) return;

    destroyHighlighter();

    final Document document1 = myEditor1.getDocument();
    final Document document2 = myEditor2.getDocument();

    DiffUtil.applyModification(sourceSide.other().selectN(document1, document2),
                               getEndLine(sourceSide.other()), getEndLine(sourceSide.other()),
                               sourceSide.selectN(document1, document2),
                               getStartLine(sourceSide), getEndLine(sourceSide));

    destroyHighlighter();
  }

  //
  // Helpers
  //

  private class MyReplaceOperation extends DiffOperation {
    @NotNull private final Side mySide;

    public MyReplaceOperation(@NotNull Side side) {
      super("Replace", AllIcons.Diff.Arrow);
      mySide = side;
    }

    @Override
    public void perform(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      assert myEditor1 != null && myEditor2 != null;

      final Document document1 = myEditor1.getDocument();
      final Document document2 = myEditor2.getDocument();

      if (!myIsValid) return;

      DiffUtil.executeWriteCommand(mySide.selectN(document1, document2), project, "Replace change", new Runnable() {
        @Override
        public void run() {
          replaceChange(mySide.other());
        }
      });
    }
  }
}
