// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class ConditionalCanBePushedInsideExpressionInspection extends BaseInspection {

  public boolean ignoreSingleArgument = true;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSingleArgument", InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.option")));
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new PushConditionalInsideFix();
  }

  private static class PushConditionalInsideFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)startElement;
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final EquivalenceChecker.Match match =
        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsMatch(thenExpression, conditionalExpression.getElseExpression());
      if (!match.isPartialMatch()) {
        return;
      }
      final PsiElement leftDiff = match.getLeftDiff();
      final PsiElement rightDiff = match.getRightDiff();
      final String expression = "(" + conditionalExpression.getCondition().getText() + " ? " +
                                leftDiff.getText() + " : " + rightDiff.getText() + ")";
      final PsiExpression newConditionalExpression =
        JavaPsiFacade.getElementFactory(project).createExpressionFromText(expression, conditionalExpression);
      final PsiElement replacedConditionalExpression = leftDiff.replace(newConditionalExpression);
      ParenthesesUtils.removeParentheses((PsiExpression)replacedConditionalExpression, false);
      CommentTracker commentTracker = new CommentTracker();
      commentTracker.markUnchanged(conditionalExpression.getCondition());
      commentTracker.replaceAndRestoreComments(conditionalExpression, thenExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalCanBePushedInsideExpressionVisitor();
  }

  private  class ConditionalCanBePushedInsideExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      final EquivalenceChecker.Match match =
        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsMatch(thenExpression, elseExpression);
      if (match.isExactMismatch() || match.isExactMatch()) {
        return;
      }
      final PsiElement leftDiff = match.getLeftDiff();
      if (!(leftDiff instanceof PsiExpression) || leftDiff.getParent() instanceof PsiStatement) {
        return;
      }
      final PsiType type = ((PsiExpression)leftDiff).getType();
      if (type == null || PsiTypes.voidType().equals(type)) {
        return;
      }
      if (ignoreSingleArgument && isOnlyArgumentOfMethodCall(leftDiff, expression)) {
        if (!isOnTheFly()) return;
        registerError(expression, ProblemHighlightType.INFORMATION);
      }
      else {
        registerError(expression, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }

    private static boolean isOnlyArgumentOfMethodCall(PsiElement element, PsiConditionalExpression conditional) {
      if (element == null) {
        return false;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionList expressionList)) {
        return false;
      }
      if (expressionList.getExpressionCount() != 1) {
        return false;
      }
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiElement greatGrandParent = ParenthesesUtils.getParentSkipParentheses(grandParent);
      return greatGrandParent == conditional;
    }
  }
}
