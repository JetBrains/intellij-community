// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffGutterRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
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

public class UnifiedDiffChangeUi {
  @NotNull private final UnifiedDiffViewer myViewer;
  @NotNull private final EditorEx myEditor;
  @NotNull private final UnifiedDiffChange myChange;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<>();

  public UnifiedDiffChangeUi(@NotNull UnifiedDiffViewer viewer, @NotNull UnifiedDiffChange change) {
    myViewer = viewer;
    myEditor = viewer.getEditor();
    myChange = change;
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

  public void installHighlighter() {
    assert myHighlighters.isEmpty() && myOperations.isEmpty();

    doInstallHighlighters();
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

  private void doInstallHighlighters() {
    myHighlighters.addAll(DiffDrawUtil.createUnifiedChunkHighlighters(myEditor,
                                                                      myChange.getDeletedRange(),
                                                                      myChange.getInsertedRange(),
                                                                      myChange.getLineFragment().getInnerFragments()));
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
    int offset = myEditor.getDocument().getLineStartOffset(myChange.getLine1());
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
          myViewer.replaceChange(myChange, sourceSide);
          myViewer.scheduleRediff();
        });
        // applyChange() will schedule rediff, but we want to try to do it in sync
        // and we can't do it inside write action
        myViewer.rediff();
      }
    };
  }
}
