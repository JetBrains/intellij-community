// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase;
import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

@ApiStatus.Internal
public class TextMergeChange extends ThreesideDiffChangeBase {

  private final @NotNull TextMergeViewer myMergeViewer;
  protected final @NotNull MergeThreesideViewer myViewer;

  private final int myIndex;
  private final @NotNull MergeLineFragment myFragment;
  private final boolean myIsImportChange;

  protected final boolean[] myResolved = new boolean[2];
  private boolean myOnesideAppliedConflict;

  private boolean myIsResolvedWithAI;

  private @Nullable MergeInnerDifferences myInnerFragments; // warning: might be out of date

  @RequiresEdt
  public TextMergeChange(int index,
                         boolean isImportChange,
                         @NotNull MergeLineFragment fragment,
                         @NotNull MergeConflictType conflictType,
                         @NotNull TextMergeViewer viewer) {
    super(conflictType);
    myMergeViewer = viewer;
    myViewer = viewer.getViewer();

    myIndex = index;
    myFragment = fragment;
    myIsImportChange = isImportChange;

    reinstallHighlighters();
  }

  @RequiresEdt
  public void reinstallHighlighters() {
    destroyHighlighters();
    installHighlighters();

    destroyOperations();
    installOperations();

    myViewer.repaintDividers();
  }

  //
  // Getters
  //

  public int getIndex() {
    return myIndex;
  }

  @RequiresEdt
  public void setResolved(@NotNull Side side, boolean value) {
    myResolved[side.getIndex()] = value;

    if (isResolved()) {
      destroyInnerHighlighters();
    }
    else {
      // Destroy only resolved side to reduce blinking
      Document document = myViewer.getEditor(side.select(ThreeSide.LEFT, ThreeSide.RIGHT)).getDocument();
      for (RangeHighlighter highlighter : myInnerHighlighters) {
        if (document.equals(highlighter.getDocument())) {
          highlighter.dispose(); // it's OK to call dispose() few times
        }
      }
    }
  }

  public boolean isResolved() {
    return myResolved[0] && myResolved[1];
  }

  public boolean isResolved(@NotNull Side side) {
    return side.select(myResolved);
  }

  public boolean isOnesideAppliedConflict() {
    return myOnesideAppliedConflict;
  }

  public void markOnesideAppliedConflict() {
    myOnesideAppliedConflict = true;
  }

  @ApiStatus.Internal
  void markChangeResolvedWithAI() {
    myIsResolvedWithAI = true;
  }

  @ApiStatus.Internal
  boolean isResolvedWithAI() {
    return myIsResolvedWithAI;
  }

  public boolean isImportChange() {
    return myIsImportChange;
  }

  @Override
  public boolean isResolved(@NotNull ThreeSide side) {
    return switch (side) {
      case LEFT -> isResolved(Side.LEFT);
      case BASE -> isResolved();
      case RIGHT -> isResolved(Side.RIGHT);
    };
  }

  public int getStartLine() {
    return myViewer.getModel().getLineStart(myIndex);
  }

  public int getEndLine() {
    return myViewer.getModel().getLineEnd(myIndex);
  }

  @Override
  public int getStartLine(@NotNull ThreeSide side) {
    if (side == ThreeSide.BASE) return getStartLine();
    return myFragment.getStartLine(side);
  }

  @Override
  public int getEndLine(@NotNull ThreeSide side) {
    if (side == ThreeSide.BASE) return getEndLine();
    return myFragment.getEndLine(side);
  }

  @Override
  public @NotNull TextDiffType getDiffType() {
    TextDiffType baseType = super.getDiffType();
    if (!myIsResolvedWithAI) return baseType;

    return new MyAIResolvedDiffType(baseType);
  }

  @Override
  protected @NotNull Editor getEditor(@NotNull ThreeSide side) {
    return myViewer.getEditor(side);
  }

  @Override
  protected @Nullable MergeInnerDifferences getInnerFragments() {
    return myInnerFragments;
  }

  public @NotNull MergeLineFragment getFragment() {
    return myFragment;
  }

  @RequiresEdt
  public void setInnerFragments(@Nullable MergeInnerDifferences innerFragments) {
    if (myInnerFragments == null && innerFragments == null) return;
    myInnerFragments = innerFragments;

    reinstallHighlighters();

    destroyInnerHighlighters();
    installInnerHighlighters();
  }

  //
  // Gutter actions
  //

  @Override
  @RequiresEdt
  protected void installOperations() {
    if (myViewer.isExternalOperationInProgress()) return;

    ContainerUtil.addIfNotNull(myOperations, createResolveOperation());
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.LEFT, OperationType.APPLY));
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.LEFT, OperationType.IGNORE));
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.RIGHT, OperationType.APPLY));
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.RIGHT, OperationType.IGNORE));
    ContainerUtil.addIfNotNull(myOperations, createResetOperation());
  }

  private @Nullable DiffGutterOperation createOperation(@NotNull ThreeSide side, @NotNull DiffGutterOperation.ModifiersRendererBuilder builder) {
    if (isResolved(side)) return null;

    EditorEx editor = myViewer.getEditor(side);
    int offset = DiffGutterOperation.lineToOffset(editor, getStartLine(side));

    return new DiffGutterOperation.WithModifiers(editor, offset, myViewer.getModifierProvider(), builder);
  }

  private @Nullable DiffGutterOperation createResolveOperation() {
    return createOperation(ThreeSide.BASE, (ctrlPressed, shiftPressed, altPressed) -> {
      return createResolveRenderer();
    });
  }

  private @Nullable DiffGutterOperation createAcceptOperation(@NotNull Side versionSide, @NotNull OperationType type) {
    ThreeSide side = versionSide.select(ThreeSide.LEFT, ThreeSide.RIGHT);
    return createOperation(side, (ctrlPressed, shiftPressed, altPressed) -> {
      if (!isChange(versionSide)) return null;

      if (type == OperationType.APPLY) {
        return createApplyRenderer(versionSide, ctrlPressed);
      }
      else {
        return createIgnoreRenderer(versionSide, ctrlPressed);
      }
    });
  }

  private @Nullable DiffGutterOperation createResetOperation() {
    if (!isResolved() || !myIsResolvedWithAI) return null;

    EditorEx editor = myViewer.getEditor(ThreeSide.BASE);
    int offset = DiffGutterOperation.lineToOffset(editor, getStartLine(ThreeSide.BASE));


    return new DiffGutterOperation.Simple(editor, offset, () -> {
      return createIconRenderer(DiffBundle.message("action.presentation.diff.revert.text"), AllIcons.Diff.Revert, false, () -> {
        myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.reset.change.command"),
                                     Collections.singletonList(this),
                                     () -> myViewer.resetResolvedChange(this));
      });
    });
  }

  private @Nullable GutterIconRenderer createApplyRenderer(final @NotNull Side side, final boolean modifier) {
    if (isResolved(side)) return null;
    Icon icon = isOnesideAppliedConflict() ? DiffUtil.getArrowDownIcon(side) : DiffUtil.getArrowIcon(side);
    return createIconRenderer(DiffBundle.message("action.presentation.diff.accept.text"), icon, isConflict(), () -> {
      myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.accept.change.command"),
                                   Collections.singletonList(this),
                                   () -> myViewer.replaceSingleChange(this, side, modifier));
    });
  }

  private @Nullable GutterIconRenderer createIgnoreRenderer(final @NotNull Side side, final boolean modifier) {
    if (isResolved(side)) return null;
    return createIconRenderer(DiffBundle.message("action.presentation.merge.ignore.text"), AllIcons.Diff.Remove, isConflict(), () -> {
      myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.ignore.change.command"), Collections.singletonList(this),
                                   () -> myViewer.ignoreChange(this, side, modifier));
    });
  }

  private @Nullable GutterIconRenderer createResolveRenderer() {
    if (!this.isConflict() || !myViewer.canResolveChangeAutomatically(this, ThreeSide.BASE)) return null;

    return createIconRenderer(DiffBundle.message("action.presentation.merge.resolve.text"), AllIcons.Diff.MagicResolve, false, () -> {
      myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.resolve.conflict.command"), Collections.singletonList(this),
                                   () -> myViewer.resolveSingleChangeAutomatically(this, ThreeSide.BASE));
    });
  }

  private static @NotNull GutterIconRenderer createIconRenderer(final @NotNull @NlsContexts.Tooltip String text,
                                                                final @NotNull Icon icon,
                                                                boolean ctrlClickVisible,
                                                                final @NotNull Runnable perform) {
    @Nls String appendix = ctrlClickVisible ? DiffBundle.message("tooltip.merge.ctrl.click.to.resolve.conflict") : null;
    final String tooltipText = DiffUtil.createTooltipText(text, appendix);
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        perform.run();
      }
    };
  }

  private enum OperationType {
    APPLY, IGNORE
  }

  //
  // State
  //

  public @NotNull State storeState() {
    return new State(
      myIndex,
      getStartLine(),
      getEndLine(),

      myResolved[0],
      myResolved[1],

      myOnesideAppliedConflict,
      myIsResolvedWithAI);
  }

  public void restoreState(@NotNull State state) {
    myResolved[0] = state.myResolved1;
    myResolved[1] = state.myResolved2;

    myOnesideAppliedConflict = state.myOnesideAppliedConflict;
    myIsResolvedWithAI = state.myIsResolvedByAI;
  }

  @ApiStatus.Internal
  void resetState() {
    myResolved[0] = false;
    myResolved[1] = false;
    myOnesideAppliedConflict = false;
    myIsResolvedWithAI = false;
  }

  public static class State extends MergeModelBase.State {
    private final boolean myResolved1;
    private final boolean myResolved2;

    private final boolean myOnesideAppliedConflict;
    private final boolean myIsResolvedByAI;

    public State(int index,
                 int startLine,
                 int endLine,
                 boolean resolved1,
                 boolean resolved2,
                 boolean onesideAppliedConflict,
                 boolean isResolvedByAI) {
      super(index, startLine, endLine);
      myResolved1 = resolved1;
      myResolved2 = resolved2;
      myOnesideAppliedConflict = onesideAppliedConflict;
      myIsResolvedByAI = isResolvedByAI;
    }
  }

  private static final JBColor AI_COLOR = new JBColor(0x834DF0, 0xA571E6); // TODO: move to platform utils

  private static class MyAIResolvedDiffType implements TextDiffType {
    private final TextDiffType myBaseType;

    private MyAIResolvedDiffType(TextDiffType baseType) {
      myBaseType = baseType;
    }

    @Override
    public @NotNull String getName() {
      return myBaseType.getName();
    }

    @Override
    public @NotNull Color getColor(@Nullable Editor editor) {
      return AI_COLOR;
    }

    @Override
    public @NotNull Color getIgnoredColor(@Nullable Editor editor) {
      return myBaseType.getIgnoredColor(editor);
    }

    @Override
    public @Nullable Color getMarkerColor(@Nullable Editor editor) {
      return myBaseType.getMarkerColor(editor);
    }
  }
}
