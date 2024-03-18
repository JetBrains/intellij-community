// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.NotNull;

public final class StringConcatenationInFormatCallInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.format.call.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInFormatCallVisitor();
  }

  private static class StringConcatenationInFormatCallVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (FormatUtils.isFormatCall(call)) {
        PsiExpressionList argumentList = call.getArgumentList();
        PsiExpression formatArgument = FormatUtils.getFormatArgument(argumentList);
        checkFormatString(call, formatArgument);
      }
      if (FormatUtils.STRING_FORMATTED.test(call)) {
        checkFormatString(call, call.getMethodExpression().getQualifierExpression());
      }
    }

    private void checkFormatString(PsiMethodCallExpression call, PsiExpression formatString) {
      formatString = PsiUtil.skipParenthesizedExprDown(formatString);
      if (!(formatString instanceof PsiPolyadicExpression polyadicExpression)) return;
      if (!ExpressionUtils.hasStringType(formatString)) return;
      if (PsiUtil.isConstantExpression(formatString)) return;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (!ContainerUtil.exists(operands, o -> ExpressionUtils.nonStructuralChildren(o).anyMatch(
        c -> c instanceof PsiReferenceExpression || c instanceof PsiMethodCallExpression || c instanceof PsiArrayAccessExpression))) {
        return;
      }
      registerError(formatString, call.getMethodExpression().getReferenceName());
    }
  }
}
