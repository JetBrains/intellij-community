// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SimpleDiffChangeUi {
  @NotNull protected final SimpleDiffViewer myViewer;
  @NotNull protected final SimpleDiffChange myChange;

  @NotNull protected final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull protected final List<GutterOperation> myOperations = new ArrayList<>();

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

    for (GutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  protected void doInstallActionHighlighters() {
    if (myChange.isSkipped()) return;

    myOperations.add(new AcceptGutterOperation(Side.LEFT));
    myOperations.add(new AcceptGutterOperation(Side.RIGHT));
  }

  private void createHighlighter(@NotNull Side side) {
    Editor editor = myViewer.getEditor(side);

    TextDiffType type = myChange.getDiffType();
    int startLine = myChange.getStartLine(side);
    int endLine = myChange.getEndLine(side);
    boolean ignored = myChange.getFragment().getInnerFragments() != null;

    myHighlighters.addAll(new DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, type)
                            .withIgnored(ignored)
                            .withExcludedInEditor(myChange.isSkipped())
                            .withExcludedInGutter(myChange.isExcluded())
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
    for (GutterOperation operation : myOperations) {
      operation.update(force);
    }
  }

  public void invalidate() {
    for (GutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  //
  // Helpers
  //

  protected abstract class GutterOperation {
    @NotNull protected final Side mySide;
    @NotNull private final RangeHighlighter myHighlighter;

    protected boolean myCtrlPressed;

    public GutterOperation(@NotNull Side side) {
      mySide = side;

      int offset = side.getStartOffset(myChange.getFragment());
      EditorEx editor = myViewer.getEditor(side);
      myHighlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                  HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                  null,
                                                                  HighlighterTargetArea.LINES_IN_RANGE);

      update(true);
    }

    public void dispose() {
      myHighlighter.dispose();
    }

    public void update(boolean force) {
      boolean isCtrlPressed = myViewer.getModifierProvider().isCtrlPressed();
      if (force || myCtrlPressed != isCtrlPressed) {
        myCtrlPressed = isCtrlPressed;
        if (myHighlighter.isValid()) myHighlighter.setGutterIconRenderer(createRenderer());
      }
    }

    @Nullable
    public abstract GutterIconRenderer createRenderer();
  }

  private class AcceptGutterOperation extends GutterOperation {
    AcceptGutterOperation(@NotNull Side side) {
      super(side);
    }

    @Nullable
    @Override
    public GutterIconRenderer createRenderer() {
      boolean isOtherEditable = DiffUtil.isEditable(myViewer.getEditor(mySide.other()));
      boolean isAppendable = myChange.getDiffType() == TextDiffType.MODIFIED;

      if (isOtherEditable) {
        if (myCtrlPressed && isAppendable) {
          return createAppendRenderer(mySide);
        }
        else {
          return createApplyRenderer(mySide);
        }
      }
      return null;
    }
  }

  @Nullable
  private GutterIconRenderer createApplyRenderer(@NotNull final Side side) {
    String text;
    Icon icon = DiffUtil.getArrowIcon(side);

    if (side == Side.LEFT && myViewer.isDiffForLocalChanges()) {
      text = "Revert";
    }
    else {
      text = "Accept";
    }

    String actionId = side.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide");
    Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    String shortcutsText = StringUtil.nullize(KeymapUtil.getShortcutsText(shortcuts));
    String tooltipText = DiffUtil.createTooltipText(text, shortcutsText);

    return createIconRenderer(side, tooltipText, icon, () -> myViewer.replaceChange(myChange, side));
  }

  @Nullable
  private GutterIconRenderer createAppendRenderer(@NotNull final Side side) {
    return createIconRenderer(side, "Append", DiffUtil.getArrowDownIcon(side), () -> myViewer.appendChange(myChange, side));
  }

  @Nullable
  private GutterIconRenderer createIconRenderer(@NotNull final Side sourceSide,
                                                @NotNull final String tooltipText,
                                                @NotNull final Icon icon,
                                                @NotNull final Runnable perform) {
    if (!DiffUtil.isEditable(myViewer.getEditor(sourceSide.other()))) return null;
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        if (!myChange.isValid()) return;
        final Project project = myViewer.getProject();
        final Document document = myViewer.getEditor(sourceSide.other()).getDocument();
        DiffUtil.executeWriteCommand(document, project, "Replace change", perform);
      }
    };
  }
}
