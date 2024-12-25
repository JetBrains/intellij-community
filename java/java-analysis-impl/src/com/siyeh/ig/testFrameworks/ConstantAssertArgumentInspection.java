// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.testFrameworks;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ConstantAssertArgumentInspection extends BaseInspection {
  private static final @NonNls Set<String> ASSERT_METHODS = Set.of(
    "assertTrue",
    "assertFalse",
    "assertNull",
    "assertNotNull"
  );

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.junit.assert.argument.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantAssertArgumentVisitor();
  }

  private static class ConstantAssertArgumentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      AssertHint assertHint = AssertHint.create(expression, methodName -> ASSERT_METHODS.contains(methodName) ? 1 : null);
      if (assertHint == null) {
        return;
      }
      final PsiExpression argument = assertHint.getFirstArgument();
      if (!PsiUtil.isConstantExpression(argument)) {
        return;
      }
      registerError(argument);
    }
  }
}
