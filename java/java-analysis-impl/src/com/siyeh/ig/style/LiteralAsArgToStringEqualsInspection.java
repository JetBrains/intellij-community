/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LiteralAsArgToStringEqualsInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    return InspectionGadgetsBundle.message(
      "literal.as.arg.to.string.equals.problem.descriptor",
      methodName);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LiteralAsArgToEqualsVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    final String methodName = (String)infos[0];
    return new SwapEqualsFix(methodName);
  }

  private static class SwapEqualsFix extends PsiUpdateModCommandQuickFix {

    private final String myMethodName;

    SwapEqualsFix(String methodName) {
      myMethodName = methodName;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      return InspectionGadgetsBundle.message("literal.as.arg.to.string.equals.flip.quickfix", myMethodName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("swap.equals.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiExpression argument = (PsiExpression)startElement;
      final PsiElement argumentList = PsiUtil.skipParenthesizedExprUp(argument.getParent());
      final PsiMethodCallExpression expression = (PsiMethodCallExpression)argumentList.getParent();
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiExpression strippedQualifier = PsiUtil.skipParenthesizedExprDown(qualifier);
      final PsiExpression strippedArgument = PsiUtil.skipParenthesizedExprDown(argument);
      if (strippedArgument == null || qualifier == null || strippedQualifier == null) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      tracker.grabComments(qualifier);
      tracker.markUnchanged(strippedQualifier);
      tracker.grabComments(argument);
      tracker.markUnchanged(strippedArgument);
      final PsiElement newArgument = strippedQualifier.copy();
      methodExpression.setQualifierExpression(strippedArgument);
      argument.replace(newArgument);
      tracker.insertCommentsBefore(expression);
    }
  }

  private static class LiteralAsArgToEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final @NonNls String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName) &&
          !HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(methodName)) {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (!(PsiUtil.skipParenthesizedExprDown(argument) instanceof PsiLiteralExpression)) {
        return;
      }
      if (!TypeUtils.isJavaLangString(argument.getType())) {
        return;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
      if (qualifier == null || qualifier instanceof PsiLiteralExpression) {
        return;
      }
      if (!TypeUtils.isJavaLangString(qualifier.getType()))  {
        return;
      }
      registerError(argument, methodName);
    }
  }
}