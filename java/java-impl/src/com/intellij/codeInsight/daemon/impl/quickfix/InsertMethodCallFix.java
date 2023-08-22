// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertMethodCallFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private final @NotNull String myMethodName;

  public InsertMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull PsiMethod method) {
    this(call, method.getName());
  }

  private InsertMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull String name) {
    super(call);
    myMethodName = name;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    return Presentation.of(QuickFixBundle.message("insert.sam.method.call.fix.name", myMethodName))
      .withPriority(PriorityAction.Priority.LOW).withFixAllOption(this);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("insert.sam.method.call.fix.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiExpression methodExpression = call.getMethodExpression();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    String replacement = methodExpression.getText() + "." + myMethodName;
    methodExpression.replace(factory.createExpressionFromText(replacement, methodExpression));
  }
}
