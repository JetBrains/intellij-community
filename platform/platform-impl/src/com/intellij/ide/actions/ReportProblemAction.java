// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class ReportProblemAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
    e.getPresentation().setEnabledAndVisible(info != null && info.getYoutrackUrl() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    SendFeedbackAction.submit(e.getProject(), appInfo.getYoutrackUrl(), SendFeedbackAction.getDescription(e.getProject()));
  }
}