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
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class NegatedIfElseInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean m_ignoreNegatedNullComparison = true;
  @SuppressWarnings("PublicField") public boolean m_ignoreNegatedZeroComparison = false;

  @Override
  @NotNull
  public String getID() {
    return "IfStatementWithNegatedCondition";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "negated.if.else.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedIfElseVisitor();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreNegatedNullComparison", InspectionGadgetsBundle.message("negated.if.else.ignore.negated.null.option")),
      checkbox("m_ignoreNegatedZeroComparison", InspectionGadgetsBundle.message("negated.if.else.ignore.negated.zero.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new NegatedIfElseFix();
  }

  private static class NegatedIfElseFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.if.else.invert.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement ifToken, @NotNull ModPsiUpdater updater) {
      final PsiIfStatement ifStatement = (PsiIfStatement)ifToken.getParent();
      assert ifStatement != null;
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      final String negatedCondition = BoolUtils.getNegatedExpressionText(condition, tracker);
      String elseText = tracker.text(elseBranch);
      final PsiElement lastChild = elseBranch.getLastChild();
      if (lastChild instanceof PsiComment comment) {
        final IElementType tokenType = comment.getTokenType();
        if (JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType)) {
          elseText += '\n';
        }
      }
      @NonNls final String newStatement = "if(" + negatedCondition + ')' + elseText + " else " + tracker.text(thenBranch);
      PsiReplacementUtil.replaceStatement(ifStatement, newStatement, tracker);
    }
  }

  private class NegatedIfElseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      if (elseBranch instanceof PsiIfStatement) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      if (!ExpressionUtils.isNegation(condition, m_ignoreNegatedNullComparison, m_ignoreNegatedZeroComparison)) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        return;
      }
      registerStatementError(statement);
    }
  }
}