// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

final class RerunAction extends AnAction {
  private final InspectionResultsView myView;

  RerunAction(InspectionResultsView view) {
    super(InspectionsBundle.message(ExperimentalUI.isNewUI() ? "inspection.action.rerun.new" : "inspection.action.rerun"),
          InspectionsBundle.message("inspection.action.rerun"),
          ExperimentalUI.isNewUI() ? AllIcons.Actions.Refresh : AllIcons.Actions.Rerun);
    myView = view;
    registerCustomShortcutSet(CommonShortcuts.getRerun(), myView);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myView.isRerunAvailable());
  }
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myView.rerun();
  }
}
