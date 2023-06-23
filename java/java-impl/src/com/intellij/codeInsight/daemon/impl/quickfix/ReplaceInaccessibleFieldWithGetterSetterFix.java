// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceInaccessibleFieldWithGetterSetterFix extends PsiUpdateModCommandAction<PsiReferenceExpression> {
  private final String myMethodName;
  private final boolean myIsSetter;

  public ReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiReferenceExpression element, @NotNull PsiMethod getter, boolean isSetter) {
    super(element);
    myMethodName = getter.getName();
    myIsSetter = isSetter;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression place, @NotNull ModPsiUpdater updater) {
    String qualifier = null;
    final PsiExpression qualifierExpression = place.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifier = qualifierExpression.getText();
    }
    Project project = context.project();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression callExpression;
    final String call = (qualifier != null ? qualifier + "." : "") + myMethodName;
    if (!myIsSetter) {
      callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(call + "()", null);
      callExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(callExpression);
      place.replace(callExpression);
    } else {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression.class);
      if (parent instanceof PsiAssignmentExpression) {
        final PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
        final String argList = rExpression != null ? rExpression.getText() : "";
        callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(call + "(" +   argList + ")", null);
        callExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(callExpression);
        parent.replace(callExpression);
      }
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression element) {
    String message = myIsSetter ? QuickFixBundle.message("replace.with.setter") : QuickFixBundle.message("replace.with.getter");
    return Presentation.of(message).withFixAllOption(this);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("replace.with.getter.setter");
  }
}
