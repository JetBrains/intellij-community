// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class InsertConstructorCallFix extends PsiUpdateModCommandAction<PsiMethod> {
  private final String myCall;

  public InsertConstructorCallFix(@NotNull PsiMethod constructor, @NonNls String call) {
    super(constructor);
    myCall = call;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod constructor) {
    if (constructor.getBody() == null || constructor.getBody().getLBrace() == null) return null;
    return Presentation.of(CommonQuickFixBundle.message("fix.insert.x", myCall)).withPriority(PriorityAction.Priority.HIGH)
      .withFixAllOption(this);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("insert.super.constructor.call.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod constructor, @NotNull ModPsiUpdater updater) {
    PsiStatement superCall =
      JavaPsiFacade.getElementFactory(context.project()).createStatementFromText(myCall,null);

    PsiCodeBlock body = Objects.requireNonNull(constructor.getBody());
    PsiJavaToken lBrace = body.getLBrace();
    body.addAfter(superCall, lBrace);
    lBrace = (PsiJavaToken) new PsiMatcherImpl(body)
              .firstChild(PsiMatchers.hasClass(PsiExpressionStatement.class))
              .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
              .firstChild(PsiMatchers.hasClass(PsiExpressionList.class))
              .firstChild(PsiMatchers.hasClass(PsiJavaToken.class))
              .dot(PsiMatchers.hasText("("))
              .getElement();
    updater.moveCaretTo(lBrace.getTextOffset() + 1);
  }
}
