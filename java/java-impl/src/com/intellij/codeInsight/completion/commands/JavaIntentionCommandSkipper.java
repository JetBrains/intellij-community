// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands;

import com.intellij.codeInsight.completion.command.commands.IntentionCommandSkipper;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateGetterOrSetterFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ExpensivePsiIntentionAction;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

class JavaIntentionCommandSkipper implements IntentionCommandSkipper {
  @Override
  public boolean skip(@NotNull CommonIntentionAction action, @NotNull PsiFile psiFile, int offset) {
    if (action instanceof ExpensivePsiIntentionAction) return true;
    LocalQuickFix fix = QuickFixWrapper.unwrap(action);
    if (fix != null) {
      ModCommandAction unwrappedAction = ModCommandService.getInstance().unwrap(fix);
      if (unwrappedAction instanceof CreateGetterOrSetterFix) return true;
    }
    return IntentionCommandSkipper.super.skip(action, psiFile, offset);
  }
}
