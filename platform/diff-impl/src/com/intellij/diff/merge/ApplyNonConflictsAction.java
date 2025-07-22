// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

class ApplyNonConflictsAction extends DumbAwareAction {
  private final @NotNull ThreeSide mySide;
  private final MergeThreesideViewer myViewer;

  ApplyNonConflictsAction(@NotNull MergeThreesideViewer viewer, @NotNull ThreeSide side, @NotNull @Nls String text) {
    String id = side.select("Diff.ApplyNonConflicts.Left", "Diff.ApplyNonConflicts", "Diff.ApplyNonConflicts.Right");
    ActionUtil.copyFrom(this, id);
    mySide = side;
    getTemplatePresentation().setText(text);
    this.myViewer = viewer;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myViewer.hasNonConflictedChanges(mySide) && !myViewer.isExternalOperationInProgress());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myViewer.applyNonConflictedChanges(mySide);
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  @Override
  public boolean useSmallerFontForTextInToolbar() {
    return true;
  }
}
