// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class ReportProblemAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
    String url = info == null ? null : info.getEAPFeedbackUrl();
    e.getPresentation().setEnabledAndVisible(url != null && url.contains("youtrack"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SendFeedbackAction.doPerformAction(e.getProject(), ApplicationInfoEx.getInstanceEx().getEAPFeedbackUrl());
  }
}
