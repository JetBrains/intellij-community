// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class UnifiedDiffChangeUi {
  @NotNull protected final UnifiedDiffViewer myViewer;
  @NotNull protected final EditorEx myEditor;
  @NotNull protected final UnifiedDiffChange myChange;

  @NotNull protected final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull protected final List<DiffGutterOperation> myOperations = new ArrayList<>();

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

    for (DiffGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty() && myOperations.isEmpty();

    doInstallHighlighters();
    doInstallActionHighlighters();
  }

  protected void doInstallActionHighlighters() {
    if (myChange.isSkipped()) return;

    boolean leftEditable = myViewer.isEditable(Side.LEFT, false);
    boolean rightEditable = myViewer.isEditable(Side.RIGHT, false);

    if (rightEditable) {
      myOperations.add(createAcceptOperation(Side.LEFT));
    }
    if (leftEditable) {
      myOperations.add(createAcceptOperation(Side.RIGHT));
    }
  }

  private void doInstallHighlighters() {
    myHighlighters.addAll(DiffDrawUtil.createUnifiedChunkHighlighters(myEditor,
                                                                      myChange.getDeletedRange(),
                                                                      myChange.getInsertedRange(),
                                                                      myChange.isExcluded(),
                                                                      myChange.isSkipped(),
                                                                      myChange.getLineFragment().getInnerFragments()));
  }

  //
  // Gutter
  //

  public void updateGutterActions() {
    for (DiffGutterOperation operation : myOperations) {
      operation.update(true);
    }
  }

  @NotNull
  protected DiffGutterOperation createOperation(@NotNull DiffGutterOperation.RendererBuilder builder) {
    int offset = myEditor.getDocument().getLineStartOffset(myChange.getLine1());

    return new DiffGutterOperation.Simple(myEditor, offset, builder);
  }

  static @NotNull @Nls String getApplyActionText(@NotNull UnifiedDiffViewer viewer, @NotNull Side sourceSide) {
    String customValue = DiffUtil.getUserData(viewer.getRequest(), viewer.getContext(),
                                              sourceSide.select(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_ACTION_TEXT,
                                                                DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_ACTION_TEXT));
    if (customValue != null) return customValue;

    return sourceSide.isLeft() ? DiffBundle.message("action.presentation.diff.revert.text")
                               : DiffBundle.message("action.presentation.diff.accept.text");
  }

  @NotNull
  static Icon getApplyIcon(@NotNull Side sourceSide) {
    return sourceSide.select(AllIcons.Diff.Revert, AllIcons.Actions.Checked);
  }

  @NotNull
  private DiffGutterOperation createAcceptOperation(@NotNull Side sourceSide) {
    return createOperation(() -> {
      if (myViewer.isStateIsOutOfDate()) return null;
      if (!myViewer.isEditable(sourceSide.other(), true)) return null;

      String text = getApplyActionText(myViewer, sourceSide);
      Icon icon = getApplyIcon(sourceSide);

      return createIconRenderer(sourceSide, text, icon);
    });
  }

  private GutterIconRenderer createIconRenderer(@NotNull final Side sourceSide,
                                                @NotNull final @NlsContexts.Tooltip String tooltipText,
                                                @NotNull final Icon icon) {
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        if (myViewer.isStateIsOutOfDate()) return;
        if (!myViewer.isEditable(sourceSide.other(), true)) return;

        final Project project = myViewer.getProject();
        final Document document = myViewer.getDocument(sourceSide.other());

        DiffUtil.executeWriteCommand(document, project, DiffBundle.message("message.replace.change.command"), () -> {
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
