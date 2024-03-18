// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ObjectEqualsCanBeEqualityInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Boolean negated = (Boolean)infos[0];
    return negated.booleanValue()
           ? InspectionGadgetsBundle.message("not.object.equals.can.be.equality.problem.descriptor")
           : InspectionGadgetsBundle.message("object.equals.can.be.equality.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Boolean not = (Boolean)infos[0];
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[1];
    return EqualsToEqualityFix.buildFix(methodCallExpression, not.booleanValue());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualsMayBeEqualityVisitor();
  }

  private static class ObjectEqualsMayBeEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      EqualityCheck check = EqualityCheck.from(expression);
      if (check == null) return;
      PsiExpression left = check.getLeft();
      PsiExpression right = check.getRight();
      if (!TypeConversionUtil.isBinaryOperatorApplicable(JavaTokenType.EQEQ, left, right, false)) {
        // replacing with == or != will generate uncompilable code
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(left.getType());
      if (aClass != null && aClass.isEnum()) {
        // Enums are reported by separate EqualsCalledOnEnumConstantInspection
        return;
      }
      final ProblemHighlightType highlightType;
      if (ClassUtils.isFinalClassWithDefaultEquals(aClass)) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else {
        if (!isOnTheFly()) return;
        highlightType = ProblemHighlightType.INFORMATION;
      }
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      final boolean negated = parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
      final PsiElement nameToken = expression.getMethodExpression().getReferenceNameElement();
      assert nameToken != null;
      registerError(nameToken, highlightType, negated, expression);
    }
  }
}
