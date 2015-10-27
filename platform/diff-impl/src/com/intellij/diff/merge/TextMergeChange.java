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

import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUtil.UpdatedLineRange;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextMergeChange extends ThreesideDiffChangeBase {
  private static final String CTRL_CLICK_TO_RESOLVE = "Ctrl+click to resolve conflict";

  @NotNull private final TextMergeTool.TextMergeViewer myMergeViewer;
  @NotNull private final TextMergeTool.TextMergeViewer.MyThreesideViewer myViewer;
  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  @NotNull private final List<RangeHighlighter> myInnerHighlighters = new ArrayList<RangeHighlighter>();

  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<MyGutterOperation>();

  private final int[] myStartLines = new int[3];
  private final int[] myEndLines = new int[3];
  private final boolean[] myResolved = new boolean[2];
  private boolean myOnesideAppliedConflict;

  @Nullable private List<MergeWordFragment> myInnerFragments;
  private boolean myInnerFragmentsDamaged;

  @CalledInAwt
  public TextMergeChange(@NotNull MergeLineFragment fragment, @NotNull TextMergeTool.TextMergeViewer viewer) {
    super(fragment, viewer.getViewer().getEditors(), ComparisonPolicy.DEFAULT);
    myMergeViewer = viewer;
    myViewer = viewer.getViewer();

    for (ThreeSide side : ThreeSide.values()) {
      myStartLines[side.getIndex()] = fragment.getStartLine(side);
      myEndLines[side.getIndex()] = fragment.getEndLine(side);
    }

    installHighlighter();
  }

  @CalledInAwt
  public void destroy() {
    destroyHighlighter();
    destroyInnerHighlighter();
  }

  @CalledInAwt
  private void installHighlighter() {
    assert myHighlighters.isEmpty();

    createHighlighter(ThreeSide.BASE);
    if (getType().isLeftChange()) createHighlighter(ThreeSide.LEFT);
    if (getType().isRightChange()) createHighlighter(ThreeSide.RIGHT);

    doInstallActionHighlighters();
  }

  @CalledInAwt
  private void installInnerHighlighter() {
    assert myInnerHighlighters.isEmpty();

    createInnerHighlighter(ThreeSide.BASE);
    if (getType().isLeftChange()) createInnerHighlighter(ThreeSide.LEFT);
    if (getType().isRightChange()) createInnerHighlighter(ThreeSide.RIGHT);
  }

  @CalledInAwt
  private void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (MyGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  @CalledInAwt
  private void destroyInnerHighlighter() {
    for (RangeHighlighter highlighter : myInnerHighlighters) {
      highlighter.dispose();
    }
    myInnerHighlighters.clear();
  }

  @CalledInAwt
  public void doReinstallHighlighter() {
    destroyHighlighter();
    installHighlighter();

    if (!myInnerFragmentsDamaged) {
      destroyInnerHighlighter();
      installInnerHighlighter();
    }

    myViewer.repaintDividers();
  }

  private void createHighlighter(@NotNull ThreeSide side) {
    Editor editor = side.select(myViewer.getEditors());

    TextDiffType type = getDiffType();
    boolean resolved = isResolved(side);
    int startLine = getStartLine(side);
    int endLine = getEndLine(side);

    TextRange range = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine, true);
    int start = range.getStartOffset();
    int end = range.getEndOffset();

    boolean ignored = !resolved && myInnerFragments != null;
    myHighlighters.addAll(DiffDrawUtil.createHighlighter(editor, start, end, type, ignored, HighlighterTargetArea.EXACT_RANGE, resolved));
    myHighlighters.addAll(DiffDrawUtil.createLineMarker(editor, startLine, endLine, type, resolved));
  }

  private void createInnerHighlighter(@NotNull ThreeSide side) {
    if (isResolved(side)) return;
    if (myInnerFragments == null) return;

    Editor editor = myViewer.getEditor(side);
    int start = DiffUtil.getLinesRange(editor.getDocument(), getStartLine(side), getEndLine(side)).getStartOffset();
    for (MergeWordFragment fragment : myInnerFragments) {
      int innerStart = start + fragment.getStartOffset(side);
      int innerEnd = start + fragment.getEndOffset(side);
      myInnerHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, getDiffType()));
    }
  }

  //
  // Getters
  //

  @CalledInAwt
  void setResolved(@NotNull Side side, boolean value) {
    myResolved[side.getIndex()] = value;

    markInnerFragmentsDamaged();
    if (isResolved()) {
      destroyInnerHighlighter();
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

  public int getStartLine(@NotNull ThreeSide side) {
    return side.select(myStartLines);
  }

  public int getEndLine(@NotNull ThreeSide side) {
    return side.select(myEndLines);
  }

  public void setStartLine(@NotNull ThreeSide side, int value) {
    myStartLines[side.getIndex()] = value;
  }

  public void setEndLine(@NotNull ThreeSide side, int value) {
    myEndLines[side.getIndex()] = value;
  }

  public void markInnerFragmentsDamaged() {
    myInnerFragmentsDamaged = true;
  }

  @CalledInAwt
  public void setInnerFragments(@Nullable List<MergeWordFragment> innerFragments) {
    myInnerFragmentsDamaged = false;
    if (myInnerFragments == null && innerFragments == null) return;
    myInnerFragments = innerFragments;
    doReinstallHighlighter();
  }

  //
  // Shift
  //

  @Nullable
  State processBaseChange(int oldLine1, int oldLine2, int shift) {
    int line1 = getStartLine(ThreeSide.BASE);
    int line2 = getEndLine(ThreeSide.BASE);

    UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);

    boolean rangeAffected = newRange.damaged ||
                            (oldLine2 >= line1 && oldLine1 <= line2); // RangeMarker can be updated in a different way
    State oldState = rangeAffected ? storeState() : null;

    if (newRange.startLine == newRange.endLine && getDiffType() == TextDiffType.DELETED && !isResolved()) {
      if (oldState == null) oldState = storeState();
      myViewer.markChangeResolved(this);
    }

    setStartLine(ThreeSide.BASE, newRange.startLine);
    setEndLine(ThreeSide.BASE, newRange.endLine);

    return oldState;
  }

  //
  // Gutter actions
  //

  private void doInstallActionHighlighters() {
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.LEFT, OperationType.APPLY));
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.LEFT, OperationType.IGNORE));
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.RIGHT, OperationType.APPLY));
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.RIGHT, OperationType.IGNORE));
  }

  @Nullable
  private MyGutterOperation createOperation(@NotNull ThreeSide side, @NotNull OperationType type) {
    if (isResolved(side)) return null;

    EditorEx editor = myViewer.getEditor(side);
    Document document = editor.getDocument();

    int line = getStartLine(side);
    int offset = line == DiffUtil.getLineCount(document) ? document.getTextLength() : document.getLineStartOffset(line);

    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                               HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                               null,
                                                                               HighlighterTargetArea.LINES_IN_RANGE);
    return new MyGutterOperation(side, highlighter, type);
  }

  public void updateGutterActions(boolean force) {
    for (MyGutterOperation operation : myOperations) {
      operation.update(force);
    }
  }

  private class MyGutterOperation {
    @NotNull private final ThreeSide mySide;
    @NotNull private final RangeHighlighter myHighlighter;
    @NotNull private final OperationType myType;

    private boolean myCtrlPressed;
    private boolean myShiftPressed;

    private MyGutterOperation(@NotNull ThreeSide side, @NotNull RangeHighlighter highlighter, @NotNull OperationType type) {
      mySide = side;
      myHighlighter = highlighter;
      myType = type;

      update(true);
    }

    public void dispose() {
      myHighlighter.dispose();
    }

    public void update(boolean force) {
      if (!force && !areModifiersChanged()) {
        return;
      }
      if (myHighlighter.isValid()) myHighlighter.setGutterIconRenderer(createRenderer());
    }

    private boolean areModifiersChanged() {
      return myCtrlPressed != myViewer.getModifierProvider().isCtrlPressed() ||
             myShiftPressed != myViewer.getModifierProvider().isShiftPressed();
    }

    @Nullable
    public GutterIconRenderer createRenderer() {
      if (mySide == ThreeSide.BASE) return null;
      Side versionSide = mySide.select(Side.LEFT, null, Side.RIGHT);
      assert versionSide != null;

      myCtrlPressed = myViewer.getModifierProvider().isCtrlPressed();
      myShiftPressed = myViewer.getModifierProvider().isShiftPressed();

      if (!isChange(versionSide)) return null;

      switch (myType) {
        case APPLY:
          return createApplyRenderer(versionSide, myCtrlPressed);
        case IGNORE:
          return createIgnoreRenderer(versionSide, myCtrlPressed);
        default:
          throw new IllegalArgumentException(myType.name());
      }
    }
  }

  @Nullable
  private GutterIconRenderer createApplyRenderer(@NotNull final Side side, final boolean modifier) {
    if (isResolved(side)) return null;
    Icon icon = isOnesideAppliedConflict() ? DiffUtil.getArrowDownIcon(side) : DiffUtil.getArrowIcon(side);
    return createIconRenderer(DiffBundle.message("merge.dialog.apply.change.action.name"), icon, isConflict(), new Runnable() {
      @Override
      public void run() {
        myViewer.executeMergeCommand("Apply change", Collections.singletonList(TextMergeChange.this), new Runnable() {
          @Override
          public void run() {
            myViewer.replaceChange(TextMergeChange.this, side, modifier);
          }
        });
      }
    });
  }

  @Nullable
  private GutterIconRenderer createIgnoreRenderer(@NotNull final Side side, final boolean modifier) {
    if (isResolved(side)) return null;
    return createIconRenderer(DiffBundle.message("merge.dialog.ignore.change.action.name"), AllIcons.Diff.Remove, isConflict(), new Runnable() {
      @Override
      public void run() {
        myViewer.executeMergeCommand(null, Collections.singletonList(TextMergeChange.this), new Runnable() {
          @Override
          public void run() {
            myViewer.ignoreChange(TextMergeChange.this, side, modifier);
          }
        });
      }
    });
  }

  @Nullable
  private GutterIconRenderer createIconRenderer(@NotNull final String text,
                                                @NotNull final Icon icon,
                                                boolean ctrlClickVisible,
                                                @NotNull final Runnable perform) {
    final String tooltipText = DiffUtil.createTooltipText(text, ctrlClickVisible ? CTRL_CLICK_TO_RESOLVE : null);
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void performAction(AnActionEvent e) {
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
      myStartLines[0],
      myStartLines[1],
      myStartLines[2],

      myEndLines[0],
      myEndLines[1],
      myEndLines[2],

      myResolved[0],
      myResolved[1],

      myOnesideAppliedConflict);
  }

  void restoreState(@NotNull State state) {
    myStartLines[0] = state.myStartLine1;
    myStartLines[1] = state.myStartLine2;
    myStartLines[2] = state.myStartLine3;

    myEndLines[0] = state.myEndLine1;
    myEndLines[1] = state.myEndLine2;
    myEndLines[2] = state.myEndLine3;

    myResolved[0] = state.myResolved1;
    myResolved[1] = state.myResolved2;

    myOnesideAppliedConflict = state.myOnesideAppliedConflict;
  }

  public static class State {
    private final int myStartLine1;
    private final int myStartLine2;
    private final int myStartLine3;

    private final int myEndLine1;
    private final int myEndLine2;
    private final int myEndLine3;

    private final boolean myResolved1;
    private final boolean myResolved2;

    private final boolean myOnesideAppliedConflict;

    public State(int startLine1,
                 int startLine2,
                 int startLine3,
                 int endLine1,
                 int endLine2,
                 int endLine3,
                 boolean resolved1,
                 boolean resolved2,
                 boolean onesideAppliedConflict) {
      myStartLine1 = startLine1;
      myStartLine2 = startLine2;
      myStartLine3 = startLine3;
      myEndLine1 = endLine1;
      myEndLine2 = endLine2;
      myEndLine3 = endLine3;
      myResolved1 = resolved1;
      myResolved2 = resolved2;
      myOnesideAppliedConflict = onesideAppliedConflict;
    }
  }
}
