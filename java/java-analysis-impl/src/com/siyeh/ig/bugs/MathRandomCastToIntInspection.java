// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class MathRandomCastToIntInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("math.random.cast.to.int.problem.descriptor", type.getPresentableText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiTypeCastExpression expression = (PsiTypeCastExpression)infos[0];
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiPolyadicExpression polyadicExpression)) {
      return null;
    }
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (JavaTokenType.ASTERISK != tokenType || polyadicExpression.getType() == null) {
      return null;
    }
    return new MathRandomCastToIntegerFix();
  }

  private static class MathRandomCastToIntegerFix extends PsiUpdateModCommandQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("math.random.cast.to.int.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      while (parent instanceof PsiPrefixExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiTypeCastExpression typeCastExpression)) {
        return;
      }
      final PsiElement grandParent = typeCastExpression.getParent();
      if (!(grandParent instanceof PsiPolyadicExpression polyadicExpression)) {
        return;
      }
      final PsiExpression operand = typeCastExpression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = polyadicExpression.getType();
      if (type == null) {
        return;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      newExpression.append("(").append(type.getCanonicalText()).append(")(");
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (final PsiExpression expression : operands) {
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(expression);
        if (token != null) {
          newExpression.append(token.getText());
        }
        if (typeCastExpression.equals(expression)) {
          newExpression.append(commentTracker.text(operand));
        }
        else {
          newExpression.append(commentTracker.text(expression));
        }
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpression(polyadicExpression, newExpression.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MathRandomCastToIntegerVisitor();
  }

  private static class MathRandomCastToIntegerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      PsiExpression operand = expression.getOperand();
      while (operand instanceof PsiPrefixExpression) {
        operand = ((PsiPrefixExpression)operand).getOperand();
      }
      if (!(operand instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiTypeElement castType = expression.getCastType();
      if (castType == null) {
        return;
      }
      final PsiType type = castType.getType();
      if (!(type instanceof PsiPrimitiveType) || PsiTypes.doubleType().equals(type) || PsiTypes.floatType().equals(type) || PsiTypes.booleanType()
        .equals(type)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"random".equals(referenceName)) {
        return;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_MATH.equals(qualifiedName) && !CommonClassNames.JAVA_LANG_STRICT_MATH.equals(qualifiedName)) {
        return;
      }
      registerError(methodCallExpression, expression, type);
    }
  }
}
