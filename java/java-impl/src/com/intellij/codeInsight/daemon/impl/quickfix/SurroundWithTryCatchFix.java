// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiResourceListElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SurroundWithTryCatchFix implements ModCommandAction {
  private final PsiElement myElement;

  public SurroundWithTryCatchFix(@NotNull PsiElement element) {
    if (element instanceof PsiExpression &&
        PsiTreeUtil.getParentOfType(element, PsiResourceListElement.class, false, PsiStatement.class) != null) {
      // We are inside resource list: there's already a suggestion to add a catch to this try.
      // Suggesting wrapping with another try-catch is confusing
      myElement = null;
      return;
    }
    if (element instanceof PsiStatement ||
        (element instanceof PsiExpression expression &&
         !(element instanceof PsiMethodReferenceExpression) &&
         CodeBlockSurrounder.canSurround(ExpressionUtils.getTopLevelExpression(expression)))) {
      myElement = element;
    } else {
      myElement = null;
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("surround.with.try.catch.fix");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return myElement != null && myElement.isValid() ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(myElement, (element, updater) -> 
      new JavaWithTryCatchSurrounder().doSurround(context, element, updater));
  }
}
