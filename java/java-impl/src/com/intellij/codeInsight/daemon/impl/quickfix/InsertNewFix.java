// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertNewFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private final PsiClass myClass;

  public InsertNewFix(@NotNull PsiMethodCallExpression methodCall, @NotNull PsiClass aClass) {
    super(methodCall);
    myClass = aClass;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("insert.new.fix");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    if (call.getNextSibling() instanceof PsiErrorElement) return null;
    if (!myClass.isValid()) return null;
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
    PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText("new X()",null);

    CommentTracker tracker = new CommentTracker();
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    assert classReference != null;
    classReference.replace(factory.createClassReferenceElement(myClass));
    PsiExpressionList argumentList = newExpression.getArgumentList();
    assert argumentList != null;
    argumentList.replace(tracker.markUnchanged(call.getArgumentList()));
    tracker.replaceAndRestoreComments(call, newExpression);
  }
}