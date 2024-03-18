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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class NegatedConditionalInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreNegatedNullComparison = true;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreNegatedZeroComparison = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ConditionalExpressionWithNegatedCondition";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "negated.conditional.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedConditionalVisitor();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreNegatedNullComparison", InspectionGadgetsBundle.message("negated.if.else.ignore.negated.null.option")),
      checkbox("m_ignoreNegatedZeroComparison", InspectionGadgetsBundle.message("negated.if.else.ignore.negated.zero.option")));
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "m_ignoreNegatedZeroComparison");
    writeBooleanOption(node, "m_ignoreNegatedZeroComparison", true);
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new NegatedConditionalFix();
  }

  private static class NegatedConditionalFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.conditional.invert.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element.getParent();
      assert conditionalExpression != null;
      final PsiExpression elseBranch = conditionalExpression.getElseExpression();
      final PsiExpression thenBranch = conditionalExpression.getThenExpression();
      final PsiExpression condition = conditionalExpression.getCondition();
      CommentTracker tracker = new CommentTracker();
      final String negatedCondition = BoolUtils.getNegatedExpressionText(condition, tracker);
      assert elseBranch != null;
      assert thenBranch != null;
      final String newStatement = negatedCondition + '?' + tracker.text(elseBranch) + ':' + tracker.text(thenBranch);
      PsiReplacementUtil.replaceExpression(conditionalExpression, newStatement, tracker);
    }
  }

  private class NegatedConditionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenBranch = expression.getThenExpression();
      if (thenBranch == null) {
        return;
      }
      final PsiExpression elseBranch = expression.getElseExpression();
      if (elseBranch == null) {
        return;
      }
      final PsiExpression condition = expression.getCondition();
      if (!ExpressionUtils.isNegation(condition, m_ignoreNegatedNullComparison, m_ignoreNegatedZeroComparison)) {
        return;
      }
      registerError(condition);
    }
  }
}