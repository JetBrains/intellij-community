// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceOperatorAssignmentWithAssignmentIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.family.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new OperatorAssignmentPredicate();
  }

  @Override
  protected String getTextForElement(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final String operator = sign.getText();
    return CommonQuickFixBundle.message("fix.replace.x.with.y", operator, "=");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression((PsiAssignmentExpression)element);
  }
}