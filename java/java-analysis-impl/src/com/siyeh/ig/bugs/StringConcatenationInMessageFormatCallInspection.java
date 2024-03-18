// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StringConcatenationInMessageFormatCallInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.message.format.call.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)infos[0];
    final String referenceName = referenceExpression.getReferenceName();
    return new StringConcatenationInFormatCallFix(referenceName);
  }

  private static class StringConcatenationInFormatCallFix extends PsiUpdateModCommandQuickFix {

    private final String variableName;

    StringConcatenationInFormatCallFix(String variableName) {
      this.variableName = variableName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("string.concatenation.in.format.call.quickfix", variableName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.in.format.call.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      final PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiExpressionList expressionList)) {
        return;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiExpression[] expressions = expressionList.getExpressions();
      final int parameter = expressions.length - 1;
      expressionList.add(rhs);
      final Object constant =
        ExpressionUtils.computeConstantExpression(lhs);
      if (constant instanceof String) {
        final PsiExpression newExpression = addParameter(lhs, parameter);
        if (newExpression == null) {
          expressionList.addAfter(lhs, binaryExpression);
        }
        else {
          expressionList.addAfter(newExpression, binaryExpression);
        }
      }
      else {
        expressionList.addAfter(lhs, binaryExpression);
      }
      binaryExpression.delete();
    }

    @Nullable
    private static PsiExpression addParameter(PsiExpression expression, int parameterNumber) {
      if (expression instanceof PsiBinaryExpression binaryExpression) {
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          return null;
        }
        final PsiExpression newExpression = addParameter(rhs, parameterNumber);
        if (newExpression == null) {
          return null;
        }
        rhs.replace(newExpression);
        return expression;
      }
      else if (expression instanceof PsiLiteralExpression literalExpression) {
        final Object value = literalExpression.getValue();
        if (!(value instanceof String)) {
          return null;
        }
        final Project project = expression.getProject();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        return factory.createExpressionFromText("\"" + value + '{' + parameterNumber + "}\"", null);
      }
      else {
        return null;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInMessageFormatCallVisitor();
  }

  private static class StringConcatenationInMessageFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isMessageFormatCall(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final PsiType type = firstArgument.getType();
      if (type == null) {
        return;
      }
      final int formatArgumentIndex;
      if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
        formatArgumentIndex = 1;
      }
      else {
        formatArgumentIndex = 0;
      }
      final PsiExpression formatArgument = arguments[formatArgumentIndex];
      final PsiType formatArgumentType = formatArgument.getType();
      if (formatArgumentType == null || !formatArgumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      if (!(formatArgument instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      if (PsiUtil.isConstantExpression(formatArgument)) {
        return;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiType lhsType = lhs.getType();
      if (lhsType == null || !lhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      registerError(formatArgument, rhs);
    }

    private static boolean isMessageFormatCall(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String referenceName = methodExpression.getReferenceName();
      if (!"format".equals(referenceName)) {
        return false;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return false;
      }
      return InheritanceUtil.isInheritor(aClass, "java.text.MessageFormat");
    }
  }
}
