// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.NotNull;

public final class StringConcatenationInFormatCallInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.format.call.problem.descriptor", infos[0]);
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInFormatCallVisitor();
  }

  private static class StringConcatenationInFormatCallVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (FormatUtils.isFormatCall(call)) {
        PsiExpressionList argumentList = call.getArgumentList();
        PsiExpression formatArgument = FormatUtils.getFormatArgument(argumentList);
        if (ExpressionUtils.isNonConstantStringConcatenation(formatArgument)) {
          registerError(formatArgument, call.getMethodExpression().getReferenceName());
        }
      }
      else if (FormatUtils.STRING_FORMATTED.test(call)) {
        PsiExpression expression = call.getMethodExpression().getQualifierExpression();
        if (ExpressionUtils.isNonConstantStringConcatenation(expression)) {
          registerError(expression, call.getMethodExpression().getReferenceName());
        }
      }
    }
  }
}
