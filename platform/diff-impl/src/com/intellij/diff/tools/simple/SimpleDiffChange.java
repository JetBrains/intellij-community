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

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SimpleDiffChange {
  @NotNull private final LineFragment myFragment;
  @Nullable private final List<DiffFragment> myInnerFragments;

  @Nullable private final EditorEx myEditor1;
  @Nullable private final EditorEx myEditor2;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  @NotNull private final List<RangeHighlighter> myActionHighlighters = new ArrayList<RangeHighlighter>();

  private boolean myIsValid = true;
  private int[] myLineStartShifts = new int[2];
  private int[] myLineEndShifts = new int[2];

  // TODO: adjust color from inner fragments - configurable
  public SimpleDiffChange(@NotNull LineFragment fragment,
                          @Nullable EditorEx editor1,
                          @Nullable EditorEx editor2,
                          boolean inlineHighlight) {
    myFragment = fragment;
    myInnerFragments = inlineHighlight ? fragment.getInnerFragments() : null;

    myEditor1 = editor1;
    myEditor2 = editor2;

    installHighlighter();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    if (myInnerFragments != null) {
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
    assert myInnerFragments != null;

    createHighlighter(Side.LEFT, true);
    createHighlighter(Side.RIGHT, true);

    for (DiffFragment fragment : myInnerFragments) {
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
    return side.getStartLine(myFragment) + side.select(myLineStartShifts);
  }

  public int getEndLine(@NotNull Side side) {
    return side.getEndLine(myFragment) + side.select(myLineEndShifts);
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
      super("Replace", DiffIcons.getReplaceIcon(Side.RIGHT));
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
