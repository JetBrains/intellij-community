// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public final class EqualsCalledOnEnumConstantInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.called.on.enum.constant.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final boolean negated = (boolean)infos[1];
    return EqualsToEqualityFix.buildFix(methodCallExpression, negated);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsCalledOnEnumValueVisitor();
  }

  private static class EqualsCalledOnEnumValueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      EqualityCheck check = EqualityCheck.from(expression);
      if (check == null) return;
      final PsiExpression left = check.getLeft();
      if (!TypeUtils.expressionHasTypeOrSubtype(left, CommonClassNames.JAVA_LANG_ENUM)) return;
      final PsiExpression right = check.getRight();

      final PsiType comparedTypeErasure = TypeConversionUtil.erasure(left.getType());
      final PsiType comparisonTypeErasure = TypeConversionUtil.erasure(right.getType());
      if (comparedTypeErasure == null || comparisonTypeErasure == null ||
          !TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
        return;
      }
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      final boolean negated = parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
      registerMethodCallError(expression, expression, negated);
    }
  }
}
