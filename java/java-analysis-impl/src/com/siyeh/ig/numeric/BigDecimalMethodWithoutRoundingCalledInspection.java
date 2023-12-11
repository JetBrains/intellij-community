// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class BigDecimalMethodWithoutRoundingCalledInspection extends BaseInspection {

  static final CallMatcher JAVA_MATH_BIG_DECIMAL =
    CallMatcher.instanceCall("java.math.BigDecimal", "setScale", "divide").parameterCount(1);

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("big.decimal.method.without.rounding.called.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BigDecimalMethodWithoutRoundingCalledVisitor();
  }

  private static class BigDecimalMethodWithoutRoundingCalledVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (JAVA_MATH_BIG_DECIMAL.test(expression)) {
        registerMethodCallError(expression);
      }
    }
  }
}
