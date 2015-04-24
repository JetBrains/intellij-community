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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class OnesideDiffChange {
  @NotNull private final OnesideDiffViewer myViewer;
  @NotNull private final EditorEx myEditor;

  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;

  private final int myLine1;
  private final int myLine2;

  @NotNull private final LineFragment myLineFragment;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  public OnesideDiffChange(@NotNull OnesideDiffViewer viewer, @NotNull ChangedBlock block, boolean innerFragments) {
    myViewer = viewer;
    myEditor = viewer.getEditor();

    myStartOffset1 = block.getStartOffset1();
    myEndOffset1 = block.getEndOffset1();
    myStartOffset2 = block.getStartOffset2();
    myEndOffset2 = block.getEndOffset2();
    myLine1 = block.getLine1();
    myLine2 = block.getLine2();
    myLineFragment = block.getLineFragment();

    installHighlighter(innerFragments);
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
  }

  public void installHighlighter(boolean innerFragments) {
    assert myHighlighters.isEmpty();

    if (innerFragments && myLineFragment.getInnerFragments() != null) {
      doInstallHighlighterWithInner();
    }
    else {
      doInstallHighlighterSimple();
    }
  }

  private void doInstallHighlighterSimple() {
    createLineHighlighters(false);
  }

  private void doInstallHighlighterWithInner() {
    List<DiffFragment> innerFragments = myLineFragment.getInnerFragments();
    assert innerFragments != null;

    createLineHighlighters(true);

    for (DiffFragment fragment : innerFragments) {
      createInlineHighlighter(TextDiffType.DELETED,
                              getStartOffset1() + fragment.getStartOffset1(),
                              getStartOffset1() + fragment.getEndOffset1());
      createInlineHighlighter(TextDiffType.INSERTED,
                              getStartOffset2() + fragment.getStartOffset2(),
                              getStartOffset2() + fragment.getEndOffset2());
    }
  }

  private void createLineHighlighters(boolean ignored) {
    boolean insertion = hasInsertion();
    boolean deletion = hasDeletion();
    if (insertion && deletion) {
      createLineMarker(TextDiffType.DELETED, getLine1(), SeparatorPlacement.TOP);
      createHighlighter(TextDiffType.DELETED, getStartOffset1(), getEndOffset1(), ignored);
      createHighlighter(TextDiffType.INSERTED, getStartOffset2(), getEndOffset2(), ignored);
      createLineMarker(TextDiffType.INSERTED, getLine2() - 1, SeparatorPlacement.BOTTOM);
    }
    else if (insertion) {
      createLineMarker(TextDiffType.INSERTED, getLine1(), SeparatorPlacement.TOP);
      createHighlighter(TextDiffType.INSERTED, getStartOffset2(), getEndOffset2(), ignored);
      createLineMarker(TextDiffType.INSERTED, getLine2() - 1, SeparatorPlacement.BOTTOM);
    }
    else if (deletion) {
      createLineMarker(TextDiffType.DELETED, getLine1(), SeparatorPlacement.TOP);
      createHighlighter(TextDiffType.DELETED, getStartOffset1(), getEndOffset1(), ignored);
      createLineMarker(TextDiffType.DELETED, getLine2() - 1, SeparatorPlacement.BOTTOM);
    }
  }

  private void createHighlighter(@NotNull TextDiffType type, int start, int end, boolean ignored) {
    myHighlighters.add(DiffDrawUtil.createHighlighter(myEditor, start, end, type, ignored));
  }

  private void createInlineHighlighter(@NotNull TextDiffType type, int start, int end) {
    myHighlighters.add(DiffDrawUtil.createInlineHighlighter(myEditor, start, end, type));
  }

  private void createLineMarker(@NotNull TextDiffType type, int line, @NotNull SeparatorPlacement placement) {
    myHighlighters.add(DiffDrawUtil.createLineMarker(myEditor, line, type, placement));
  }

  public int getStartOffset1() {
    return myStartOffset1;
  }

  public int getEndOffset1() {
    return myEndOffset1;
  }

  public int getStartOffset2() {
    return myStartOffset2;
  }

  public int getEndOffset2() {
    return myEndOffset2;
  }

  public int getLine1() {
    return myLine1;
  }

  public int getLine2() {
    return myLine2;
  }

  private boolean hasInsertion() {
    return myStartOffset2 != myEndOffset2;
  }

  private boolean hasDeletion() {
    return myStartOffset1 != myEndOffset1;
  }
}
