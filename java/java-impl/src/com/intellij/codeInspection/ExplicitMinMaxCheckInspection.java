// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class ExplicitMinMaxCheckInspection extends AbstractBaseJavaLocalInspectionTool {

  public boolean disableForNonIntegralTypes = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionsBundle.message("inspection.explicit.min.max.check.disable.for.non.integral"),
                      "disableForNonIntegralTypes");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        PsiBinaryExpression condition = getCondition(statement.getCondition());
        if (condition == null) return;
        PsiStatement thenStatement = ControlFlowUtils.stripBraces(statement.getThenBranch());
        if (thenStatement == null) return;
        PsiStatement elseStatement = ControlFlowUtils.stripBraces(statement.getElseBranch());
        if (elseStatement == null) return;
        if (thenStatement.getClass() != elseStatement.getClass()) return;

        PsiExpression thenExpression = getBranchExpression(thenStatement);
        if (thenExpression == null) return;
        PsiExpression elseExpression = getBranchExpression(elseStatement);
        if (elseExpression == null) return;
        if (thenExpression instanceof PsiAssignmentExpression && elseExpression instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression thenAssignment = (PsiAssignmentExpression)thenExpression;
          PsiAssignmentExpression elseAssignment = (PsiAssignmentExpression)elseExpression;
          if (!thenAssignment.getOperationTokenType().equals(elseAssignment.getOperationTokenType())) return;
          EquivalenceChecker equivalenceChecker = EquivalenceChecker.getCanonicalPsiEquivalence();
          if (!equivalenceChecker.expressionsAreEquivalent(thenAssignment.getLExpression(), elseAssignment.getLExpression())) return;
          visitConditional(statement.getFirstChild(), condition, thenAssignment.getRExpression(), elseAssignment.getRExpression());
        }
        else {
          visitConditional(statement.getFirstChild(), condition, thenExpression, elseExpression);
        }
      }

      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        PsiBinaryExpression condition = getCondition(expression.getCondition());
        if (condition == null) return;
        visitConditional(expression, condition, expression.getThenExpression(), expression.getElseExpression());
      }

      private void visitConditional(@NotNull PsiElement element,
                                    @NotNull PsiBinaryExpression condition,
                                    @Nullable PsiExpression thenExpression,
                                    @Nullable PsiExpression elseExpression) {
        if (thenExpression == null || elseExpression == null) return;
        PsiExpression left = condition.getLOperand();
        PsiExpression right = condition.getROperand();
        if (right == null) return;
        if (!hasCompatibleType(left) || !hasCompatibleType(right) ||
            PsiUtil.isIncrementDecrementOperation(left) || PsiUtil.isIncrementDecrementOperation(right)) {
          return;
        }
        EquivalenceChecker equivalenceChecker = EquivalenceChecker.getCanonicalPsiEquivalence();
        boolean useMathMin = equivalenceChecker.expressionsAreEquivalent(left, elseExpression);
        if (!useMathMin && !equivalenceChecker.expressionsAreEquivalent(left, thenExpression)) return;
        if (!equivalenceChecker.expressionsAreEquivalent(right, useMathMin ? thenExpression : elseExpression)) return;
        useMathMin ^= JavaTokenType.LT.equals(condition.getOperationTokenType());
        holder.registerProblem(element,
                               InspectionsBundle.message("inspection.explicit.min.max.check.description", useMathMin ? "min" : "max"),
                               new ReplaceWithMinMaxFix(useMathMin));
      }

      private boolean hasCompatibleType(@NotNull PsiExpression expression) {
        PsiType type = expression.getType();
        if (type == null) return false;
        int rank = TypeConversionUtil.getTypeRank(type);
        if (rank < TypeConversionUtil.INT_RANK || rank > TypeConversionUtil.DOUBLE_RANK) return false;
        return !disableForNonIntegralTypes || rank <= TypeConversionUtil.LONG_RANK;
      }
    };
  }

  @Nullable
  private static PsiBinaryExpression getCondition(@Nullable PsiExpression expression) {
    PsiBinaryExpression condition = tryCast(ParenthesesUtils.stripParentheses(expression), PsiBinaryExpression.class);
    if (condition == null) return null;
    IElementType tokenType = condition.getOperationTokenType();
    return JavaTokenType.LT.equals(tokenType) || JavaTokenType.GT.equals(tokenType) ? condition : null;
  }

  @Nullable
  private static PsiExpression getBranchExpression(@NotNull PsiStatement statement) {
    if (statement instanceof PsiReturnStatement) {
      return ((PsiReturnStatement)statement).getReturnValue();
    }
    else if (statement instanceof PsiBreakStatement) {
      return ((PsiBreakStatement)statement).getExpression();
    }
    else if (statement instanceof PsiExpressionStatement) {
      return PsiTreeUtil.findChildOfType(((PsiExpressionStatement)statement).getExpression(), PsiAssignmentExpression.class, false);
    }
    return null;
  }

  private static class ReplaceWithMinMaxFix implements LocalQuickFix {

    private final boolean myUseMathMin;

    @Contract(pure = true)
    private ReplaceWithMinMaxFix(boolean useMathMin) {
      myUseMathMin = useMathMin;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Math." + (myUseMathMin ? "min" : "max"));
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiConditionalExpression) {
        PsiBinaryExpression condition = getCondition(((PsiConditionalExpression)element).getCondition());
        if (condition == null) return;
        String replacement = createReplacement(condition);
        if (replacement == null) return;
        PsiReplacementUtil.replaceExpression((PsiExpression)element, replacement, new CommentTracker());
        return;
      }
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
      if (ifStatement == null) return;
      PsiBinaryExpression condition = getCondition(ifStatement.getCondition());
      if (condition == null) return;
      String replacement = createReplacement(condition);
      if (replacement == null) return;
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      if (thenBranch == null) return;
      PsiExpression toReplace = getBranchExpression(thenBranch);
      if (toReplace instanceof PsiAssignmentExpression) toReplace = ((PsiAssignmentExpression)toReplace).getRExpression();
      if (toReplace == null) return;
      PsiReplacementUtil.replaceExpression(toReplace, replacement, new CommentTracker());
      CommentTracker tracker = new CommentTracker();
      tracker.text(thenBranch);
      PsiReplacementUtil.replaceStatement(ifStatement, thenBranch.getText(), tracker);
    }

    @Nullable
    private String createReplacement(@NotNull PsiBinaryExpression condition) {
      PsiExpression left = condition.getLOperand();
      PsiExpression right = condition.getROperand();
      if (right == null) return null;
      return CommonClassNames.JAVA_LANG_MATH + (myUseMathMin ? ".min" : ".max") + "(" + left.getText() + "," + right.getText() + ")";
    }
  }
}
