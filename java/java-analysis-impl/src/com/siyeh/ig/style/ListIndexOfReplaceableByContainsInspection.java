// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ListIndexOfReplaceableByContainsInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiBinaryExpression expression = (PsiBinaryExpression)infos[0];
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
    final String text;
    CommentTracker tracker = new CommentTracker();
    if (lhs instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)lhs;
      text = createContainsExpressionText(callExpression, false,
                                          expression.getOperationTokenType(), tracker);
    }
    else {
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      assert callExpression != null;
      text = createContainsExpressionText(callExpression, true,
                                          expression.getOperationTokenType(), tracker);
    }
    return InspectionGadgetsBundle.message(
      "expression.can.be.replaced.problem.descriptor", text);
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new IndexOfReplaceableByContainsFix();
  }

  private static class IndexOfReplaceableByContainsFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiBinaryExpression expression = (PsiBinaryExpression)startElement;
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      CommentTracker tracker = new CommentTracker();
      final String newExpressionText;
      if (lhs instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression =
          (PsiMethodCallExpression)lhs;
        newExpressionText =
          createContainsExpressionText(callExpression, false, expression.getOperationTokenType(), tracker);
      }
      else {
        final PsiMethodCallExpression callExpression =
          (PsiMethodCallExpression)rhs;
        assert callExpression != null;
        newExpressionText =
          createContainsExpressionText(callExpression, true, expression.getOperationTokenType(), tracker);
      }

      PsiReplacementUtil.replaceExpression(expression, newExpressionText, tracker);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", "indexOf()", "contains()");
    }
  }

  static String createContainsExpressionText(@NotNull PsiMethodCallExpression call, boolean flipped, IElementType tokenType, CommentTracker tracker) {
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    final String qualifierText;
    if (qualifierExpression == null) {
      qualifierText = "";
    }
    else {
      qualifierText = tracker.text(qualifierExpression);
    }
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression expression = argumentList.getExpressions()[0];
    @NonNls final String newExpressionText = qualifierText + ".contains(" + tracker.text(expression) + ')';
    if (tokenType.equals(JavaTokenType.EQEQ)) {
      return '!' + newExpressionText;
    }
    else if (!flipped && (tokenType.equals(JavaTokenType.LT) ||
                          tokenType.equals(JavaTokenType.LE))) {
      return '!' + newExpressionText;
    }
    else if (flipped && (tokenType.equals(JavaTokenType.GT) ||
                         tokenType.equals(JavaTokenType.GE))) {
      return '!' + newExpressionText;
    }
    return newExpressionText;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IndexOfReplaceableByContainsVisitor();
  }

  private static class IndexOfReplaceableByContainsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      if (rhs == null || lhs == null) return;
      if (!ComparisonUtils.isComparison(expression)) return;
      if (lhs instanceof PsiMethodCallExpression) {
        if (canBeReplacedByContains(lhs, rhs, false,
                                    expression.getOperationTokenType())) {
          registerError(expression, expression);
        }
      }
      else if (rhs instanceof PsiMethodCallExpression) {
        if (canBeReplacedByContains(rhs, lhs, true,
                                    expression.getOperationTokenType())) {
          registerError(expression, expression);
        }
      }
    }

    private static boolean canBeReplacedByContains(
      PsiExpression lhs,
      PsiExpression rhs, boolean flipped, IElementType tokenType) {
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)lhs;
      if (!isIndexOfCall(callExpression)) {
        return false;
      }
      final Object object =
        ExpressionUtils.computeConstantExpression(rhs);
      if (!(object instanceof Integer integer)) {
        return false;
      }
      final int constant = integer.intValue();
      if (flipped) {
        if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                               JavaTokenType.LT.equals(tokenType) ||
                               JavaTokenType.EQEQ.equals(tokenType) ||
                               JavaTokenType.GE.equals(tokenType))) {
          return true;
        }
        else if (constant == 0 &&
                 (JavaTokenType.LE.equals(tokenType) ||
                  JavaTokenType.GT.equals(tokenType))) {
          return true;
        }
      }
      else {
        if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                               JavaTokenType.GT.equals(tokenType) ||
                               JavaTokenType.EQEQ.equals(tokenType) ||
                               JavaTokenType.LE.equals(tokenType))) {
          return true;
        }
        else if (constant == 0 &&
                 (JavaTokenType.GE.equals(tokenType) ||
                  JavaTokenType.LT.equals(tokenType))) {
          return true;
        }
      }
      return false;
    }

    private static boolean isIndexOfCall(
      @NotNull PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.INDEX_OF.equals(methodName)) {
        return false;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      final PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        return false;
      }
      final Project project = expression.getProject();
      final GlobalSearchScope projectScope =
        GlobalSearchScope.allScope(project);
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass javaUtilListClass =
        psiFacade.findClass(CommonClassNames.JAVA_UTIL_LIST,
                            projectScope);
      if (javaUtilListClass == null) {
        return false;
      }
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiClassType javaUtilListType =
        factory.createType(javaUtilListClass);
      return javaUtilListType.isAssignableFrom(qualifierType);
    }
  }
}