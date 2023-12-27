// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SimpleDiffChangeUi {
  @NotNull protected final SimpleDiffViewer myViewer;
  @NotNull protected final SimpleDiffChange myChange;

  @NotNull protected final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull protected final List<DiffGutterOperation> myOperations = new ArrayList<>();

  public SimpleDiffChangeUi(@NotNull SimpleDiffViewer viewer, @NotNull SimpleDiffChange change) {
    myViewer = viewer;
    myChange = change;
  }

  public void installHighlighter(@Nullable SimpleDiffChange previousChange) {
    assert myHighlighters.isEmpty() && myOperations.isEmpty();

    createHighlighter(Side.LEFT);
    createHighlighter(Side.RIGHT);

    List<DiffFragment> innerFragments = myChange.getFragment().getInnerFragments();
    for (DiffFragment fragment : ContainerUtil.notNullize(innerFragments)) {
      createInlineHighlighter(fragment, Side.LEFT);
      createInlineHighlighter(fragment, Side.RIGHT);
    }

    createNonSquashedChangesSeparator(previousChange, Side.LEFT);
    createNonSquashedChangesSeparator(previousChange, Side.RIGHT);

    doInstallActionHighlighters();
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

  protected void doInstallActionHighlighters() {
    if (myChange.isSkipped()) return;

    myOperations.add(createAcceptOperation(Side.LEFT));
    myOperations.add(createAcceptOperation(Side.RIGHT));
  }

  @ApiStatus.Internal
  protected void createHighlighter(@NotNull Side side) {
    Editor editor = myViewer.getEditor(side);

    TextDiffType type = myChange.getDiffType();
    int startLine = myChange.getStartLine(side);
    int endLine = myChange.getEndLine(side);
    boolean ignored = myChange.getFragment().getInnerFragments() != null;
    boolean alignedSides = myViewer.needAlignChanges();

    myHighlighters.addAll(new DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, type)
                            .withIgnored(ignored)
                            .withExcludedInEditor(myChange.isSkipped())
                            .withExcludedInGutter(myChange.isExcluded())
                            .withAlignedSides(alignedSides)
                            .done());
  }

  private void createInlineHighlighter(@NotNull DiffFragment innerFragment, @NotNull Side side) {
    if (myChange.isSkipped()) return;

    int start = side.getStartOffset(innerFragment);
    int end = side.getEndOffset(innerFragment);
    TextDiffType type = DiffUtil.getDiffType(innerFragment);

    int startOffset = side.getStartOffset(myChange.getFragment());
    start += startOffset;
    end += startOffset;

    Editor editor = myViewer.getEditor(side);
    myHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, start, end, type));
  }

  private void createNonSquashedChangesSeparator(@Nullable SimpleDiffChange previousChange, @NotNull Side side) {
    if (previousChange == null) return;

    int startLine = myChange.getStartLine(side);
    int endLine = myChange.getEndLine(side);

    int prevStartLine = previousChange.getStartLine(side);
    int prevEndLine = previousChange.getEndLine(side);

    if (startLine == endLine) return;
    if (prevStartLine == prevEndLine) return;
    if (prevEndLine != startLine) return;

    myHighlighters.addAll(DiffDrawUtil.createLineMarker(myViewer.getEditor(side), startLine, TextDiffType.MODIFIED));
  }

  public void updateGutterActions(boolean force) {
    for (DiffGutterOperation operation : myOperations) {
      operation.update(force);
    }
  }

  public void invalidate() {
    for (DiffGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  public boolean drawDivider(@NotNull DiffDividerDrawUtil.DividerPaintable.Handler handler) {
    int startLine1 = myChange.getStartLine(Side.LEFT);
    int endLine1 = myChange.getEndLine(Side.LEFT);
    int startLine2 = myChange.getStartLine(Side.RIGHT);
    int endLine2 = myChange.getEndLine(Side.RIGHT);
    TextDiffType type = myChange.getDiffType();

    if (myViewer.needAlignChanges()) {
      return handler.processAligned(startLine1, endLine1, startLine2, endLine2, type);
    }
    else {
      return handler.processExcludable(startLine1, endLine1, startLine2, endLine2, type,
                                       myChange.isExcluded(), myChange.isSkipped());
    }
  }

  //
  // Helpers
  //

  @NotNull
  protected DiffGutterOperation createOperation(@NotNull Side side,
                                                @NotNull DiffGutterOperation.ModifiersRendererBuilder builder) {
    int offset = side.getStartOffset(myChange.getFragment());
    EditorEx editor = myViewer.getEditor(side);

    return new DiffGutterOperation.WithModifiers(editor, offset, myViewer.getModifierProvider(), builder);
  }

  @NotNull
  private DiffGutterOperation createAcceptOperation(@NotNull Side side) {
    return createOperation(side, (ctrlPressed, shiftPressed, altPressed) -> {
      boolean isOtherEditable = myViewer.isEditable(side.other());
      boolean isAppendable = myChange.getDiffType() == TextDiffType.MODIFIED;

      if (isOtherEditable) {
        if (ctrlPressed && isAppendable) {
          return createAppendRenderer(side);
        }
        else {
          return createApplyRenderer(side);
        }
      }
      return null;
    });
  }

  static @NotNull @Nls String getApplyActionText(@NotNull SimpleDiffViewer viewer, @NotNull Side sourceSide) {
    String customValue = DiffUtil.getUserData(viewer.getRequest(), viewer.getContext(),
                                              sourceSide.select(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_ACTION_TEXT,
                                                                DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_ACTION_TEXT));
    if (customValue != null) return customValue;

    if (sourceSide == Side.LEFT && viewer.isDiffForLocalChanges()) {
      return DiffBundle.message("action.presentation.diff.revert.text");
    }

    return DiffBundle.message("action.presentation.diff.accept.text");
  }

  private GutterIconRenderer createApplyRenderer(@NotNull final Side side) {
    String text = getApplyActionText(myViewer, side);
    Icon icon = DiffUtil.getArrowIcon(side);

    String actionId = side.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide");
    Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    String shortcutsText = StringUtil.nullize(KeymapUtil.getShortcutsText(shortcuts));
    String tooltipText = DiffUtil.createTooltipText(text, shortcutsText);

    return createIconRenderer(side, tooltipText, icon, () -> myViewer.replaceChange(myChange, side));
  }

  private GutterIconRenderer createAppendRenderer(@NotNull final Side side) {
    return createIconRenderer(side, DiffBundle.message("action.presentation.diff.append.text"), DiffUtil.getArrowDownIcon(side),
                              () -> myViewer.appendChange(myChange, side));
  }

  private GutterIconRenderer createIconRenderer(@NotNull final Side sourceSide,
                                                @NotNull @Nls final String tooltipText,
                                                @NotNull final Icon icon,
                                                @NotNull final Runnable perform) {
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        if (!myChange.isValid()) return;
        final Project project = myViewer.getProject();
        final Document document = myViewer.getEditor(sourceSide.other()).getDocument();
        DiffUtil.executeWriteCommand(document, project, DiffBundle.message("message.replace.change.command"), perform);
      }
    };
  }
}
