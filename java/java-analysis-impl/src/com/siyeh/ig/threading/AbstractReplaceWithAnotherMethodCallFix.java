// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

abstract class AbstractReplaceWithAnotherMethodCallFix extends PsiUpdateModCommandQuickFix {
  protected abstract String getMethodName();

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", getMethodName() + "()");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement methodNameElement, @NotNull ModPsiUpdater updater) {
    final PsiReferenceExpression methodExpression = (PsiReferenceExpression)methodNameElement.getParent();
    assert methodExpression != null;
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    CommentTracker commentTracker = new CommentTracker();
    if (qualifier == null) {
      PsiReplacementUtil.replaceExpression(methodExpression, getMethodName(), commentTracker);
    }
    else {
      final String qualifierText = commentTracker.text(qualifier);
      PsiReplacementUtil.replaceExpression(methodExpression, qualifierText + '.' + getMethodName(), commentTracker);
    }
  }
}