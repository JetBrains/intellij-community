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

import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUtil.UpdatedLineRange;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UnifiedDiffChange {
  @NotNull private final UnifiedDiffViewer myViewer;
  @NotNull private final EditorEx myEditor;

  // Boundaries of this change in myEditor. If current state is out-of-date - approximate value.
  private int myLine1;
  private int myLine2;

  @NotNull private final LineFragment myLineFragment;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<>();

  public UnifiedDiffChange(@NotNull UnifiedDiffViewer viewer, @NotNull ChangedBlock block) {
    myViewer = viewer;
    myEditor = viewer.getEditor();

    myLine1 = block.getLine1();
    myLine2 = block.getLine2();
    myLineFragment = block.getLineFragment();

    LineRange deleted = block.getRange1();
    LineRange inserted = block.getRange2();

    installHighlighter(deleted, inserted);
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (MyGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  private void installHighlighter(@NotNull LineRange deleted, @NotNull LineRange inserted) {
    assert myHighlighters.isEmpty();

    doInstallHighlighters(deleted, inserted);
    doInstallActionHighlighters();
  }

  private void doInstallActionHighlighters() {
    boolean leftEditable = myViewer.isEditable(Side.LEFT, false);
    boolean rightEditable = myViewer.isEditable(Side.RIGHT, false);

    if (leftEditable && rightEditable) {
      myOperations.add(createOperation(Side.LEFT));
      myOperations.add(createOperation(Side.RIGHT));
    }
    else if (rightEditable) {
      myOperations.add(createOperation(Side.LEFT));
    }
  }

  private void doInstallHighlighters(@NotNull LineRange deleted, @NotNull LineRange inserted) {
    myHighlighters.addAll(DiffDrawUtil.createUnifiedChunkHighlighters(myEditor, deleted, inserted, myLineFragment.getInnerFragments()));
  }

  public int getLine1() {
    return myLine1;
  }

  public int getLine2() {
    return myLine2;
  }

  /*
   * Warning: It does not updated on document change. Check myViewer.isStateInconsistent() before use.
   */
  @NotNull
  public LineFragment getLineFragment() {
    return myLineFragment;
  }

  public void processChange(int oldLine1, int oldLine2, int shift) {
    UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(myLine1, myLine2, oldLine1, oldLine2, shift);
    myLine1 = newRange.startLine;
    myLine2 = newRange.endLine;
  }

  //
  // Gutter
  //

  public void updateGutterActions() {
    for (MyGutterOperation operation : myOperations) {
      operation.update();
    }
  }

  @NotNull
  private MyGutterOperation createOperation(@NotNull Side sourceSide) {
    int offset = myEditor.getDocument().getLineStartOffset(myLine1);
    RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                                 HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                                 null,
                                                                                 HighlighterTargetArea.LINES_IN_RANGE);
    return new MyGutterOperation(sourceSide, highlighter);
  }

  private class MyGutterOperation {
    @NotNull private final Side mySide;
    @NotNull private final RangeHighlighter myHighlighter;

    private MyGutterOperation(@NotNull Side sourceSide, @NotNull RangeHighlighter highlighter) {
      mySide = sourceSide;
      myHighlighter = highlighter;

      update();
    }

    public void dispose() {
      myHighlighter.dispose();
    }

    public void update() {
      if (myHighlighter.isValid()) myHighlighter.setGutterIconRenderer(createRenderer());
    }

    @Nullable
    public GutterIconRenderer createRenderer() {
      if (myViewer.isStateIsOutOfDate()) return null;
      if (!myViewer.isEditable(mySide.other(), true)) return null;

      if (mySide.isLeft()) {
        return createIconRenderer(mySide, "Revert", AllIcons.Diff.Remove);
      }
      else {
        return createIconRenderer(mySide, "Accept", AllIcons.Actions.Checked);
      }
    }
  }

  @Nullable
  private GutterIconRenderer createIconRenderer(@NotNull final Side sourceSide,
                                                @NotNull final String tooltipText,
                                                @NotNull final Icon icon) {
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        if (myViewer.isStateIsOutOfDate()) return;
        if (!myViewer.isEditable(sourceSide.other(), true)) return;

        final Project project = myViewer.getProject();
        final Document document = myViewer.getDocument(sourceSide.other());

        DiffUtil.executeWriteCommand(document, project, "Replace change", () -> {
          myViewer.replaceChange(UnifiedDiffChange.this, sourceSide);
          myViewer.scheduleRediff();
        });
        // applyChange() will schedule rediff, but we want to try to do it in sync
        // and we can't do it inside write action
        myViewer.rediff();
      }
    };
  }
}
