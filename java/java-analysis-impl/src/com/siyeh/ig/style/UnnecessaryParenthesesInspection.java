/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryParenthesesInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreClarifyingParentheses = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreParenthesesOnConditionals = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreParenthesesOnLambdaParameter = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.parentheses.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreClarifyingParentheses", InspectionGadgetsBundle.message("unnecessary.parentheses.option")),
      checkbox("ignoreParenthesesOnConditionals", InspectionGadgetsBundle.message("unnecessary.parentheses.conditional.option")),
      checkbox("ignoreParenthesesOnLambdaParameter",
               InspectionGadgetsBundle.message("ignore.parentheses.around.single.no.formal.type.lambda.parameter")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryParenthesesVisitor();
  }

  private class UnnecessaryParenthesesFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.parentheses.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiParameterList parameterList) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        final String text = Objects.requireNonNull(parameterList.getParameter(0)).getName() + "->{}";
        final PsiLambdaExpression expression = (PsiLambdaExpression)factory.createExpressionFromText(text, element);
        element.replace(expression.getParameterList());
      } else {
        ParenthesesUtils.removeParentheses((PsiCaseLabelElement)element, ignoreClarifyingParentheses);
      }
    }
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryParenthesesFix();
  }

  private class UnnecessaryParenthesesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitParameterList(@NotNull PsiParameterList list) {
      super.visitParameterList(list);
      if (!ignoreParenthesesOnLambdaParameter && list.getParent() instanceof PsiLambdaExpression && list.getParametersCount() == 1) {
        final PsiParameter parameter = Objects.requireNonNull(list.getParameter(0));
        if (parameter.getTypeElement() == null && list.getFirstChild() != parameter && list.getLastChild() != parameter) {
          registerError(list);
        }
      }
    }

    @Override
    public void visitParenthesizedPattern(@NotNull PsiParenthesizedPattern pattern) {
      final PsiElement parent = pattern.getParent();
      if (parent instanceof PsiParenthesizedPattern) {
        return;
      }
      if (!ErrorUtil.containsDeepError(pattern)) {
        registerError(pattern);
      }
      super.visitParenthesizedPattern(pattern);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        return;
      }
      if (ignoreParenthesesOnConditionals && parent instanceof PsiConditionalExpression conditionalExpression) {
        final PsiExpression condition = conditionalExpression.getCondition();
        if (expression == condition) {
          return;
        }
      }
      if (!ParenthesesUtils.areParenthesesNeeded(expression, ignoreClarifyingParentheses) &&
          !ErrorUtil.containsError(expression) && !ErrorUtil.containsError(expression.getExpression())) {
        registerError(expression);
        return;
      }
      super.visitParenthesizedExpression(expression);
    }
  }
}
