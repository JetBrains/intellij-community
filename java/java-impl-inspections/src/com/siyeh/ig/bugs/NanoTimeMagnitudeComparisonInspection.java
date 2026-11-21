// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

public final class NanoTimeMagnitudeComparisonInspection extends BaseInspection {

  private static final CallMatcher NANO_TIME =
    CallMatcher.staticCall("java.lang.System", "nanoTime").parameterCount(0);

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("nanotime.magnitude.comparison.problem.descriptor");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new NanoTimeMagnitudeComparisonVisitor();
  }

  private static class NanoTimeMagnitudeComparisonVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.LT) && !tokenType.equals(JavaTokenType.GT) &&
          !tokenType.equals(JavaTokenType.LE) && !tokenType.equals(JavaTokenType.GE)) {
        return;
      }
      if (isNanoTimeCall(expression.getLOperand()) || isNanoTimeCall(expression.getROperand())) {
        registerError(expression);
      }
    }

    private static boolean isNanoTimeCall(PsiExpression operand) {
      final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(operand);
      return expression instanceof PsiMethodCallExpression call && NANO_TIME.test(call);
    }
  }
}
