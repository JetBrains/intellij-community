// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class MagicResolvedConflictsAction extends DumbAwareAction {
  private final MergeThreesideViewer myViewer;

  public MagicResolvedConflictsAction(MergeThreesideViewer viewer) {
    ActionUtil.copyFrom(this, "Diff.MagicResolveConflicts");
    this.myViewer = viewer;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myViewer.hasResolvableConflictedChanges() && !myViewer.isExternalOperationInProgress());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myViewer.applyResolvableConflictedChanges();
  }
}
