// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public final class EnableOptimizeImportsOnTheFlyFix implements IntentionAction, LowPriorityAction{
  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("enable.optimize.imports.on.the.fly");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return BaseIntentionAction.canModify(file)
           && file instanceof PsiJavaFile
           && !CodeInsightWorkspaceSettings.getInstance(project).isOptimizeImportsOnTheFly();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    CodeInsightWorkspaceSettings.getInstance(project).setOptimizeImportsOnTheFly(true);
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project, true);
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
