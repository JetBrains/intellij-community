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
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;

public class TextMergeChange extends ThreesideDiffChangeBase {

  @NotNull private final TextMergeViewer myMergeViewer;
  @NotNull private final TextMergeViewer.MyThreesideViewer myViewer;

  private final int myIndex;
  @NotNull private final MergeLineFragment myFragment;

  private final boolean[] myResolved = new boolean[2];
  private boolean myOnesideAppliedConflict;

  @Nullable private MergeInnerDifferences myInnerFragments; // warning: might be out of date

  @RequiresEdt
  public TextMergeChange(int index,
                         @NotNull MergeLineFragment fragment,
                         @NotNull MergeConflictType conflictType,
                         @NotNull TextMergeViewer viewer) {
    super(conflictType);
    myMergeViewer = viewer;
    myViewer = viewer.getViewer();

    myIndex = index;
    myFragment = fragment;

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
  void setResolved(@NotNull Side side, boolean value) {
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

  @Override
  public boolean isResolved(@NotNull ThreeSide side) {
    switch (side) {
      case LEFT:
        return isResolved(Side.LEFT);
      case BASE:
        return isResolved();
      case RIGHT:
        return isResolved(Side.RIGHT);
      default:
        throw new IllegalArgumentException(side.toString());
    }
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

  @NotNull
  @Override
  protected Editor getEditor(@NotNull ThreeSide side) {
    return myViewer.getEditor(side);
  }

  @Nullable
  @Override
  protected MergeInnerDifferences getInnerFragments() {
    return myInnerFragments;
  }

  @NotNull
  public MergeLineFragment getFragment() {
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
    ContainerUtil.addIfNotNull(myOperations, createResolveOperation());
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.LEFT, OperationType.APPLY));
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.LEFT, OperationType.IGNORE));
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.RIGHT, OperationType.APPLY));
    ContainerUtil.addIfNotNull(myOperations, createAcceptOperation(Side.RIGHT, OperationType.IGNORE));
  }

  @Nullable
  private DiffGutterOperation createOperation(@NotNull ThreeSide side, @NotNull DiffGutterOperation.ModifiersRendererBuilder builder) {
    if (isResolved(side)) return null;

    EditorEx editor = myViewer.getEditor(side);
    int offset = DiffGutterOperation.lineToOffset(editor, getStartLine(side));

    return new DiffGutterOperation.WithModifiers(editor, offset, myViewer.getModifierProvider(), builder);
  }

  @Nullable
  private DiffGutterOperation createResolveOperation() {
    return createOperation(ThreeSide.BASE, (ctrlPressed, shiftPressed, altPressed) -> {
      return createResolveRenderer();
    });
  }

  @Nullable
  private DiffGutterOperation createAcceptOperation(@NotNull Side versionSide, @NotNull OperationType type) {
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

  @Nullable
  private GutterIconRenderer createApplyRenderer(@NotNull final Side side, final boolean modifier) {
    if (isResolved(side)) return null;
    Icon icon = isOnesideAppliedConflict() ? DiffUtil.getArrowDownIcon(side) : DiffUtil.getArrowIcon(side);
    return createIconRenderer(DiffBundle.message("action.presentation.diff.accept.text"), icon, isConflict(), () -> {
      myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.accept.change.command"),
                                   Collections.singletonList(this),
                                   () -> myViewer.replaceChange(this, side, modifier));
    });
  }

  @Nullable
  private GutterIconRenderer createIgnoreRenderer(@NotNull final Side side, final boolean modifier) {
    if (isResolved(side)) return null;
    return createIconRenderer(DiffBundle.message("action.presentation.merge.ignore.text"), AllIcons.Diff.Remove, isConflict(), () -> {
      myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.ignore.change.command"), Collections.singletonList(this),
                                   () -> myViewer.ignoreChange(this, side, modifier));
    });
  }

  @Nullable
  private GutterIconRenderer createResolveRenderer() {
    if (!this.isConflict() || !myViewer.canResolveChangeAutomatically(this, ThreeSide.BASE)) return null;

    return createIconRenderer(DiffBundle.message("action.presentation.merge.resolve.text"), AllIcons.Diff.MagicResolve, false, () -> {
      myViewer.executeMergeCommand(DiffBundle.message("merge.dialog.resolve.conflict.command"), Collections.singletonList(this),
                                   () -> myViewer.resolveChangeAutomatically(this, ThreeSide.BASE));
    });
  }

  @NotNull
  private static GutterIconRenderer createIconRenderer(@NotNull final @NlsContexts.Tooltip String text,
                                                       @NotNull final Icon icon,
                                                       boolean ctrlClickVisible,
                                                       @NotNull final Runnable perform) {
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

  @NotNull
  State storeState() {
    return new State(
      myIndex,
      getStartLine(),
      getEndLine(),

      myResolved[0],
      myResolved[1],

      myOnesideAppliedConflict);
  }

  void restoreState(@NotNull State state) {
    myResolved[0] = state.myResolved1;
    myResolved[1] = state.myResolved2;

    myOnesideAppliedConflict = state.myOnesideAppliedConflict;
  }

  public static class State extends MergeModelBase.State {
    private final boolean myResolved1;
    private final boolean myResolved2;

    private final boolean myOnesideAppliedConflict;

    public State(int index,
                 int startLine,
                 int endLine,
                 boolean resolved1,
                 boolean resolved2,
                 boolean onesideAppliedConflict) {
      super(index, startLine, endLine);
      myResolved1 = resolved1;
      myResolved2 = resolved2;
      myOnesideAppliedConflict = onesideAppliedConflict;
    }
  }
}
