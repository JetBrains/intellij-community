// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ToggleCompletionHintsAction extends ToggleAction implements ActionRemoteBehaviorSpecification.BackendOnly {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile == null || !"Java".equals(psiFile.getLanguage().getDisplayName())) {
      e.getPresentation().setVisible(false);
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = state;
  }
}
