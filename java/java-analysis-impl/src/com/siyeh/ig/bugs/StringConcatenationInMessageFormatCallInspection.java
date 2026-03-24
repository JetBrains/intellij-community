// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.format.MessageFormatUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public final class StringConcatenationInMessageFormatCallInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.message.format.call.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    PsiPolyadicExpression expression = (PsiPolyadicExpression)infos[0];
    PsiExpression previous = null;
    for (PsiExpression operand : expression.getOperands()) {
      if (!PsiUtil.isConstantExpression(operand) && !ExpressionUtils.isStringLiteral(previous)) {
        return null;
      }
      previous = operand;
    }
    return new StringConcatenationInFormatCallFix();
  }

  private static class StringConcatenationInFormatCallFix extends PsiUpdateModCommandQuickFix {

    StringConcatenationInFormatCallFix() {}

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("string.concatenation.in.format.call.quickfix");
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.in.format.call.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiPolyadicExpression polyadicExpression) || polyadicExpression.getLastChild() instanceof PsiErrorElement) {
        return;
      }
      final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(polyadicExpression, PsiExpressionList.class);
      if (expressionList == null) {
        return;
      }
      int parameter = expressionList.getExpressionCount() - 1;
      StringBuilder newFormatArgument = new StringBuilder();
      PsiElement child = polyadicExpression.getFirstChild();
      PsiElement anchor = child;
      PsiElement previousLiteral = null;
      while (child != null) {
        if (child instanceof PsiExpression expression) {
          if (ExpressionUtils.isStringLiteral(expression)) {
            previousLiteral = expression;
          }
          else if (!PsiUtil.isConstantExpression(expression)) {
            assert previousLiteral != null;
            appendText(anchor, previousLiteral, newFormatArgument);
            String parameterText = "{" + parameter + '}';
            String text = previousLiteral.getText();
            if (!text.contains(parameterText)) {
              boolean textBlock = text.endsWith("\"\"\"");
              newFormatArgument.append(text, 0, textBlock ? text.length() - 3 : text.length() - 1)
                .append(parameterText)
                .append(textBlock ? "\"\"\"" : "\"");
            }
            else {
              newFormatArgument.append(text);
            }
            parameter++;
            anchor = child = child.getNextSibling();
            expressionList.add(expression);
            continue;
          }
        }
        child = child.getNextSibling();
      }
      appendText(anchor, null, newFormatArgument);
      PsiReplacementUtil.replaceExpression(polyadicExpression, newFormatArgument.toString());
    }

    private static void appendText(PsiElement start, PsiElement end, StringBuilder result) {
      PsiElement element = start;
      while (element != end) {
        result.append(element.getText());
        element = element.getNextSibling();
      }
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInMessageFormatCallVisitor();
  }

  private static class StringConcatenationInMessageFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MessageFormatUtil.PATTERN_METHODS.test(expression)) {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      if (arguments.length < 2) {
        return;
      }
      final PsiExpression formatArgument = arguments[0];
      if (formatArgument.getLastChild() instanceof PsiErrorElement) {
        return;
      }
      if (ExpressionUtils.isNonConstantStringConcatenation(formatArgument)) {
        registerError(formatArgument, formatArgument);
      }
    }
  }
}
