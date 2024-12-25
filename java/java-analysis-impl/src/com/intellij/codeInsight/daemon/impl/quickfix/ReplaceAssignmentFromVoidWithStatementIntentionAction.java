// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAssignmentFromVoidWithStatementIntentionAction extends PsiUpdateModCommandAction<PsiElement> {
  private final @NotNull SmartPsiElementPointer<PsiExpression> myLExpr;

  public ReplaceAssignmentFromVoidWithStatementIntentionAction(@NotNull PsiElement parent, @NotNull PsiExpression lExpr) {
    super(parent);
    myLExpr = SmartPointerManager.createPointer(lExpr);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("remove.left.side.of.assignment");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return myLExpr.getElement() != null ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression lExpr = myLExpr.getElement();
    if (lExpr != null) {
      element.replace(lExpr);
    }
  }
}
