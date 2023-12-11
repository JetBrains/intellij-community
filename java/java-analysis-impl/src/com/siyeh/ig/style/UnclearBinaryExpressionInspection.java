// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnclearBinaryExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "UnclearExpression";
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unclear.binary.expression.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnclearBinaryExpressionFix();
  }

  private static class UnclearBinaryExpressionFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      replaceElement(startElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnclearBinaryExpressionVisitor();
  }

  private static class UnclearBinaryExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpression(@NotNull PsiExpression expression) {
      super.visitExpression(expression);
      final PsiElement parent = expression.getParent();
      if (mightBeConfusingExpression(parent) || !isUnclearExpression(expression, parent)) {
        return;
      }
      if (ErrorUtil.containsDeepError(expression)) {
        return;
      }
      registerError(expression);
    }
  }

  public static boolean mightBeConfusingExpression(@Nullable PsiElement element) {
    return (element instanceof PsiPolyadicExpression) || (element instanceof PsiConditionalExpression) ||
           (element instanceof PsiInstanceOfExpression) || (element instanceof PsiAssignmentExpression) ||
           (element instanceof PsiParenthesizedExpression);
  }

  @Contract("null, _ -> false")
  public static boolean isUnclearExpression(PsiExpression expression, PsiElement parent) {
    if (expression instanceof PsiAssignmentExpression assignmentExpression) {
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (parent instanceof PsiVariable) {
        if (!tokenType.equals(JavaTokenType.EQ)) {
          return true;
        }
      }
      else if (parent instanceof PsiAssignmentExpression nestedAssignment) {
        final IElementType nestedTokenType = nestedAssignment.getOperationTokenType();
        if (!tokenType.equals(nestedTokenType)) {
          return true;
        }
      }
      return isUnclearExpression(assignmentExpression.getRExpression(), assignmentExpression);
    }
    else if (expression instanceof PsiConditionalExpression conditionalExpression) {
      if (PsiUtilCore.hasErrorElementChild(expression)) {
        return false;
      }
      return (parent instanceof PsiConditionalExpression) ||
             isUnclearExpression(conditionalExpression.getCondition(), conditionalExpression) ||
             isUnclearExpression(conditionalExpression.getThenExpression(), conditionalExpression) ||
             isUnclearExpression(conditionalExpression.getElseExpression(), conditionalExpression);
    }
    else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      if ((parent instanceof PsiConditionalExpression) || (parent instanceof PsiPolyadicExpression) ||
          (parent instanceof PsiInstanceOfExpression)) {
        return true;
      }
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        final PsiType type = operand.getType();
        if ((type == null) || type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          return false;
        }
        if (isUnclearExpression(operand, polyadicExpression)) {
          return true;
        }
      }
    }
    else if (expression instanceof PsiInstanceOfExpression instanceOfExpression) {
      if ((parent instanceof PsiPolyadicExpression) || (parent instanceof PsiConditionalExpression)) {
        return true;
      }
      return isUnclearExpression(instanceOfExpression.getOperand(), instanceOfExpression);
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression nestedExpression = parenthesizedExpression.getExpression();
      return isUnclearExpression(nestedExpression, parenthesizedExpression);
    }
    return false;
  }

  public static void replaceElement(PsiElement element) {
    if (!(element instanceof PsiExpression expression)) {
      return;
    }
    final String newExpressionText = createReplacementText(expression, new StringBuilder()).toString();
    PsiReplacementUtil.replaceExpression(expression, newExpressionText);
  }

  private static StringBuilder createReplacementText(@Nullable PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (parent instanceof PsiConditionalExpression) ||
                                  (parent instanceof PsiInstanceOfExpression) ||
                                  (parent instanceof PsiPolyadicExpression);
      appendText(polyadicExpression, parentheses, out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      for (PsiElement child = expression.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiExpression unwrappedExpression) {
          createReplacementText(unwrappedExpression, out);
        }
        else {
          out.append(child.getText());
        }
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (parent instanceof PsiPolyadicExpression) || (parent instanceof PsiConditionalExpression);
      appendText(expression, parentheses, out);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (parent instanceof PsiConditionalExpression) || (parent instanceof PsiPolyadicExpression) ||
                                  (parent instanceof PsiInstanceOfExpression);
      appendText(expression, parentheses, out);
    }
    else if (expression instanceof PsiAssignmentExpression assignmentExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = !isSimpleAssignment(assignmentExpression, parent);
      appendText(assignmentExpression, parentheses, out);
    }
    else if (expression != null) {
      out.append(expression.getText());
    }
    return out;
  }

  private static boolean isSimpleAssignment(PsiAssignmentExpression assignmentExpression, PsiElement parent) {
    final IElementType parentTokenType;
    if (parent instanceof PsiExpressionStatement) {
      return true;
    }
    else if (parent instanceof PsiAssignmentExpression parentAssignmentExpression) {
      parentTokenType = parentAssignmentExpression.getOperationTokenType();
    }
    else if (parent instanceof PsiVariable) {
      parentTokenType = JavaTokenType.EQ;
    }
    else {
      return false;
    }
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    return parentTokenType.equals(tokenType);
  }

  private static void appendText(PsiExpression expression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    for (PsiElement child = expression.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiExpression) {
        createReplacementText((PsiExpression)child, out);
      }
      else {
        out.append(child.getText());
      }
    }
    if (parentheses) {
      out.append(')');
    }
  }
}
