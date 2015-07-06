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
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUtil.UpdatedLineRange;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TextMergeChange extends ThreesideDiffChangeBase {
  @NotNull private final TextMergeTool.TextMergeViewer myMergeViewer;
  @NotNull private final TextMergeTool.TextMergeViewer.MyThreesideViewer myViewer;
  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<MyGutterOperation>();

  private int[] myStartLines = new int[3];
  private int[] myEndLines = new int[3];
  private final boolean[] myResolved = new boolean[2];

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

  protected void installHighlighter() {
    assert myHighlighters.isEmpty();

    createHighlighter(ThreeSide.BASE);
    if (getType().isLeftChange()) createHighlighter(ThreeSide.LEFT);
    if (getType().isRightChange()) createHighlighter(ThreeSide.RIGHT);

    doInstallActionHighlighters();
  }

  @CalledInAwt
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

  @CalledInAwt
  public void doReinstallHighlighter() {
    destroyHighlighter();
    installHighlighter();
    myViewer.repaintDividers();
  }

  private void createHighlighter(@NotNull ThreeSide side) {
    Editor editor = side.select(myViewer.getEditors());
    Document document = editor.getDocument();

    TextDiffType type = getDiffType();
    boolean resolved = isResolved(side);
    int startLine = getStartLine(side);
    int endLine = getEndLine(side);

    int start;
    int end;
    if (startLine == endLine) {
      start = end = startLine < DiffUtil.getLineCount(document) ? document.getLineStartOffset(startLine) : document.getTextLength();
    }
    else {
      start = document.getLineStartOffset(startLine);
      end = document.getLineEndOffset(endLine - 1);
      if (end < document.getTextLength()) end++;
    }

    myHighlighters.add(DiffDrawUtil.createHighlighter(editor, start, end, type, false, HighlighterTargetArea.EXACT_RANGE, resolved));

    if (startLine == endLine) {
      if (startLine != 0) {
        myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, true, resolved));
      }
    }
    else {
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, startLine, type, SeparatorPlacement.TOP, false, resolved));
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, false, resolved));
    }
  }

  //
  // Getters
  //

  @CalledInAwt
  public void markResolved() {
    if (isResolved()) return;
    myResolved[0] = true;
    myResolved[1] = true;
    myViewer.onChangeResolved(this);
    myViewer.reinstallHighlighter(this);
  }

  @CalledInAwt
  public void markResolved(@NotNull Side side) {
    if (isResolved(side)) return;
    myResolved[side.getIndex()] = true;
    if (isResolved()) myViewer.onChangeResolved(this);
    myViewer.reinstallHighlighter(this);
  }

  public boolean isResolved() {
    return myResolved[0] && myResolved[1];
  }

  public boolean isResolved(@NotNull Side side) {
    return side.select(myResolved);
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

  //
  // Shift
  //

  public boolean processBaseChange(int oldLine1, int oldLine2, int shift) {
    int line1 = getStartLine(ThreeSide.BASE);
    int line2 = getEndLine(ThreeSide.BASE);
    int baseIndex = ThreeSide.BASE.getIndex();

    UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myStartLines[baseIndex] = newRange.startLine;
    myEndLines[baseIndex] = newRange.endLine;

    boolean rangeAffected = oldLine2 >= line1 && oldLine1 <= line2; // RangeMarker can be updated in a different way

    if (newRange.startLine == newRange.endLine && getDiffType() == TextDiffType.DELETED) {
      markResolved();
    }

    return newRange.damaged || rangeAffected;
  }

  //
  // Gutter actions
  //

  private void doInstallActionHighlighters() {
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.LEFT));
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.BASE));
    ContainerUtil.addIfNotNull(myOperations, createOperation(ThreeSide.RIGHT));
  }

  @Nullable
  private MyGutterOperation createOperation(@NotNull ThreeSide side) {
    if (isResolved(side)) return null;

    EditorEx editor = myViewer.getEditor(side);
    Document document = editor.getDocument();

    int line = getStartLine(side);
    int offset = line == DiffUtil.getLineCount(document) ? document.getTextLength() : document.getLineStartOffset(line);

    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                               HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                               null,
                                                                               HighlighterTargetArea.LINES_IN_RANGE);
    return new MyGutterOperation(side, highlighter);
  }

  public void updateGutterActions(boolean force) {
    for (MyGutterOperation operation : myOperations) {
      operation.update(force);
    }
  }

  private class MyGutterOperation {
    @NotNull private final ThreeSide mySide;
    @NotNull private final RangeHighlighter myHighlighter;

    private boolean myCtrlPressed;
    private boolean myShiftPressed;

    private MyGutterOperation(@NotNull ThreeSide side, @NotNull RangeHighlighter highlighter) {
      mySide = side;
      myHighlighter = highlighter;

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
      myCtrlPressed = myViewer.getModifierProvider().isCtrlPressed();
      myShiftPressed = myViewer.getModifierProvider().isShiftPressed();

      if (mySide == ThreeSide.BASE) {
        return createRevertRenderer();
      }
      else {
        Side versionSide = mySide.select(Side.LEFT, null, Side.RIGHT);
        assert versionSide != null;

        if (!isChange(versionSide)) return null;

        boolean isAppendable = getStartLine(mySide) != getEndLine(mySide) &&
                               (getStartLine(ThreeSide.BASE) != getEndLine(ThreeSide.BASE) || isConflict());

        if (myShiftPressed) {
          return createRevertRenderer();
        }
        if (myCtrlPressed && isAppendable) {
          return createAppendRenderer(versionSide);
        }
        return createApplyRenderer(versionSide);
      }
    }
  }

  @Nullable
  private GutterIconRenderer createApplyRenderer(@NotNull final Side side) {
    return createIconRenderer(DiffBundle.message("merge.dialog.apply.change.action.name"), AllIcons.Diff.Arrow, new Runnable() {
      @Override
      public void run() {
        final Document document = myViewer.getEditor(ThreeSide.BASE).getDocument();
        DiffUtil.executeWriteCommand(document, myViewer.getProject(), "Apply change", new Runnable() {
          @Override
          public void run() {
            myViewer.replaceChange(TextMergeChange.this, side);
          }
        });
      }
    });
  }

  @Nullable
  private GutterIconRenderer createAppendRenderer(@NotNull final Side side) {
    return createIconRenderer(DiffBundle.message("merge.dialog.append.change.action.name"), AllIcons.Diff.ArrowLeftDown, new Runnable() {
      @Override
      public void run() {
        final Document document = myViewer.getEditor(ThreeSide.BASE).getDocument();
        DiffUtil.executeWriteCommand(document, myViewer.getProject(), "Apply change", new Runnable() {
          @Override
          public void run() {
            myViewer.appendChange(TextMergeChange.this, side);
          }
        });
      }
    });
  }

  @Nullable
  private GutterIconRenderer createRevertRenderer() {
    return createIconRenderer(DiffBundle.message("merge.dialog.ignore.change.action.name"), AllIcons.Diff.Remove, new Runnable() {
      @Override
      public void run() {
        markResolved();
      }
    });
  }

  @Nullable
  private GutterIconRenderer createIconRenderer(@NotNull final String tooltipText,
                                                @NotNull final Icon icon,
                                                @NotNull final Runnable perform) {
    return new GutterIconRenderer() {
      @NotNull
      @Override
      public Icon getIcon() {
        return icon;
      }

      public boolean isNavigateAction() {
        return true;
      }

      @Nullable
      @Override
      public AnAction getClickAction() {
        return new DumbAwareAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            perform.run();
          }
        };
      }

      @Override
      public boolean equals(Object obj) {
        return obj == this;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }

      @Nullable
      @Override
      public String getTooltipText() {
        return tooltipText;
      }
    };
  }
}
