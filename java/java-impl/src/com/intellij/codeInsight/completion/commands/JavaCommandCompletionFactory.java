// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands;

import com.intellij.codeInsight.completion.command.CommandCompletionFactory;
import com.intellij.codeInsight.completion.command.commands.IntentionCommandSkipper;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateGetterOrSetterFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ExpensivePsiIntentionAction;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

class JavaCommandCompletionFactory implements CommandCompletionFactory, DumbAware {

  @Override
  public boolean isApplicable(@NotNull PsiFile psiFile, int offset) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    PsiElement elementAt = psiFile.findElementAt(offset);
    if (elementAt == null) return true;
    if (!(elementAt.getParent() instanceof PsiParameterList)) return true;
    PsiElement prevLeaf = PsiTreeUtil.prevLeaf(elementAt, true);
    if (!(prevLeaf instanceof PsiJavaToken javaToken && javaToken.textMatches("."))) return true;
    PsiElement prevPrevLeaf = PsiTreeUtil.prevLeaf(prevLeaf, true);
    if (PsiTreeUtil.getParentOfType(prevPrevLeaf, PsiTypeElement.class) != null) return false;
    return true;
  }

  static class JavaIntentionCommandSkipper implements IntentionCommandSkipper {
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
}
