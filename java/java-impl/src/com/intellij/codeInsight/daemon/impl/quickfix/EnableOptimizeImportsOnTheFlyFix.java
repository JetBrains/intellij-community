// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnableOptimizeImportsOnTheFlyFix implements ModCommandAction {
  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (BaseIntentionAction.canModify(context.file())
        && context.file() instanceof PsiJavaFile
        && !CodeInsightWorkspaceSettings.getInstance(context.project()).isOptimizeImportsOnTheFly()) {
      return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.LOW);
    }
    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("enable.optimize.imports.on.the.fly");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.updateOption(context.file(), "CodeInsightWorkspaceSettings.optimizeImportsOnTheFly", true);
  }
}
