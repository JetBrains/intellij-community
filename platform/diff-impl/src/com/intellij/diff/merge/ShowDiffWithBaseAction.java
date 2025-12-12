// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

class ShowDiffWithBaseAction extends DumbAwareAction {
  private final @NotNull ThreeSide mySide;
  private final MergeThreesideViewer myViewer;

  ShowDiffWithBaseAction(MergeThreesideViewer viewer, @NotNull ThreeSide side) {
    mySide = side;
    String actionId = mySide.select("Diff.CompareWithBase.Left", "Diff.CompareWithBase.Result", "Diff.CompareWithBase.Right");
    ActionUtil.copyFrom(this, actionId);
    this.myViewer = viewer;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!myViewer.isExternalOperationInProgress());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DiffContent baseContent = ThreeSide.BASE.select(myViewer.getMergeRequest().getContents());
    String baseTitle = ThreeSide.BASE.select(myViewer.getMergeRequest().getContentTitles());

    DiffContent otherContent = mySide.select(myViewer.getRequest().getContents());
    String otherTitle = mySide.select(myViewer.getRequest().getContentTitles());

    SimpleDiffRequest request = new SimpleDiffRequest(myViewer.getRequest().getTitle(), baseContent, otherContent, baseTitle, otherTitle);

    ThreeSide currentSide = myViewer.getCurrentSide();
    LogicalPosition currentPosition = DiffUtil.getCaretPosition(myViewer.getCurrentEditor());

    LogicalPosition resultPosition = myViewer.transferPosition(currentSide, mySide, currentPosition);
    request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, resultPosition.line));

    DiffManager.getInstance().showDiff(myViewer.getProject(), request, new DiffDialogHints(null, myViewer.getComponent()));
  }
}
