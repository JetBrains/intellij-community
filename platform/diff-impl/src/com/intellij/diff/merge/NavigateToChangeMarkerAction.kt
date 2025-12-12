// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DefaultFlagsProvider;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions;
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase;
import com.intellij.openapi.vcs.ex.LineStatusTrackerMarkerRenderer;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;


class NavigateToChangeMarkerAction extends DumbAwareAction {
  private final boolean myGoToNext;
  private final MergeThreesideViewer myViewer;

  protected NavigateToChangeMarkerAction(MergeThreesideViewer viewer, boolean goToNext) {
    myGoToNext = goToNext;
    // TODO: reuse ShowChangeMarkerAction
    ActionUtil.copyFrom(this, myGoToNext ? "VcsShowNextChangeMarker" : "VcsShowPrevChangeMarker");
    this.myViewer = viewer;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myViewer.getTextSettings().isEnableLstGutterMarkersInMerge());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!myViewer.myLineStatusTracker.isValid()) return;

    int line = myViewer.getEditor().getCaretModel().getLogicalPosition().line;
    Range targetRange = myGoToNext ? myViewer.myLineStatusTracker.getNextRange(line) : myViewer.myLineStatusTracker.getPrevRange(line);
    if (targetRange != null) {
      myViewer.new MyLineStatusMarkerRenderer(myViewer.myLineStatusTracker).scrollAndShow(myViewer.getEditor(), targetRange);
    }
  }
}

private class MyLineStatusMarkerRenderer extends LineStatusTrackerMarkerRenderer {
  private final @NotNull LineStatusTrackerBase<?> myTracker;

  MyLineStatusMarkerRenderer(@NotNull LineStatusTrackerBase<?> tracker) {
    super(tracker, editor -> editor == myViewer.getEditor());
    myTracker = tracker;
  }

  @Override
  public void scrollAndShow(@NotNull Editor editor, @NotNull Range range) {
    if (!myTracker.isValid()) return;
    final Document document = myTracker.getDocument();
    int line = Math.min(!range.hasLines() ? range.getLine2() : range.getLine2() - 1, getLineCount(document) - 1);

    int[] startLines = new int[]{
      myViewer.transferPosition(ThreeSide.BASE, ThreeSide.LEFT, new LogicalPosition(line, 0)).line,
      line,
      myViewer.transferPosition(ThreeSide.BASE, ThreeSide.RIGHT, new LogicalPosition(line, 0)).line
    };

    for (ThreeSide side : ThreeSide.values()) {
      DiffUtil.moveCaret(myViewer.getEditor(side), side.select(startLines));
    }

    myViewer.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    showAfterScroll(editor, range);
  }

  @Override
  protected @NotNull List<AnAction> createToolbarActions(@NotNull Editor editor,
                                                         @NotNull Range range,
                                                         @Nullable Point mousePosition) {
    List<AnAction> actions = new ArrayList<>();
    actions.add(new LineStatusMarkerPopupActions.ShowPrevChangeMarkerAction(editor, myTracker, range, this));
    actions.add(new LineStatusMarkerPopupActions.ShowNextChangeMarkerAction(editor, myTracker, range, this));
    actions.add(new MyRollbackLineStatusRangeAction(editor, range));
    actions.add(new LineStatusMarkerPopupActions.ShowLineStatusRangeDiffAction(editor, myTracker, range));
    actions.add(new LineStatusMarkerPopupActions.CopyLineStatusRangeAction(editor, myTracker, range));
    actions.add(new LineStatusMarkerPopupActions.ToggleByWordDiffAction(editor, myTracker, range, mousePosition, this));
    return actions;
  }

  private final class MyRollbackLineStatusRangeAction extends LineStatusMarkerPopupActions.RangeMarkerAction {
    private MyRollbackLineStatusRangeAction(@NotNull Editor editor, @NotNull Range range) {
      super(editor, myTracker, range, IdeActions.SELECTED_CHANGES_ROLLBACK);
    }

    @Override
    protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
      return true;
    }

    @Override
    protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
      DiffUtil.moveCaretToLineRangeIfNeeded(editor, range.getLine1(), range.getLine2());
      myTracker.rollbackChanges(range);
    }
  }

  @Override
  protected void paintGutterMarkers(@NotNull Editor editor, @NotNull List<? extends Range> ranges, @NotNull Graphics g) {
    int framingBorder = JBUIScale.scale(2);
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, framingBorder);
  }

  @Override
  public String toString() {
    return "MergeThreesideViewer.MyLineStatusMarkerRenderer{" +
           "myTracker=" + myTracker +
           '}';
  }
}
