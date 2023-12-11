// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class RedundantLengthCheckInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
        PsiExpression condition = ifStatement.getCondition();
        Set<IElementType> tokens = new HashSet<>();
        while (condition instanceof PsiPolyadicExpression poly &&
               (poly.getOperationTokenType() == JavaTokenType.ANDAND || poly.getOperationTokenType() == JavaTokenType.OROR)) {
          tokens.add(poly.getOperationTokenType());
          condition = ArrayUtil.getLastElement(poly.getOperands());
        }
        if (tokens.size() > 1) return;
        if (!(PsiUtil.skipParenthesizedExprDown(condition) instanceof PsiBinaryExpression binOp)) return;
        PsiExpression lengthExpression = getValueComparedWithZero(binOp);
        PsiExpression arrayExpression = ExpressionUtils.getArrayFromLengthExpression(lengthExpression);
        if (arrayExpression == null) return;
        IElementType tokenType = binOp.getOperationTokenType();
        if ((tokenType == JavaTokenType.NE || tokenType == JavaTokenType.GT) && !tokens.contains(JavaTokenType.OROR)) {
          processIsNotNull(ifStatement, condition, arrayExpression);
        }
        else if ((tokenType == JavaTokenType.EQEQ || tokenType == JavaTokenType.LE) && !tokens.contains(JavaTokenType.ANDAND)) {
          processIsNull(ifStatement, condition, arrayExpression);
        }
      }

      private void processIsNotNull(@NotNull PsiIfStatement ifStatement,
                                    @NotNull PsiExpression condition,
                                    @NotNull PsiExpression arrayExpression) {
        if (ifStatement.getElseBranch() != null) return;
        if (!(ControlFlowUtils.stripBraces(ifStatement.getThenBranch()) instanceof PsiForeachStatement forEach)) return;
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(forEach.getIteratedValue(), arrayExpression)) {
          holder.problem(condition, InspectionGadgetsBundle.message("inspection.redundant.length.check.display.name")).fix(new SimplifyBooleanExpressionFix(condition, true)).register();
        }
      }

      private void processIsNull(@NotNull PsiIfStatement ifStatement,
                                 @NotNull PsiExpression condition,
                                 @NotNull PsiExpression arrayExpression) {
        if (!(ifStatement.getParent() instanceof PsiCodeBlock block)) return;
        PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        boolean exit;
        PsiStatement expectedAfterStatement = null;
        if (thenBranch instanceof PsiContinueStatement cont &&
            block.getParent() instanceof PsiBlockStatement blockStatement &&
            cont.findContinuedStatement() == blockStatement.getParent()) {
          exit = true;
        }
        else if (thenBranch instanceof PsiReturnStatement ret) {
          exit = true;
          if (ret.getReturnValue() != null || !(block.getParent() instanceof PsiParameterListOwner)) {
            expectedAfterStatement = ret;
          }
        }
        else if (thenBranch instanceof PsiYieldStatement yield) {
          exit = true;
          expectedAfterStatement = yield;
        }
        else {
          exit = false;
        }
        if (!exit) return;
        PsiStatement elseStatement = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
        PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(ifStatement, PsiStatement.class);
        PsiForeachStatement forEach;
        EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
        if (equivalence.statementsAreEquivalent(nextStatement, expectedAfterStatement)) {
          forEach = ObjectUtils.tryCast(elseStatement, PsiForeachStatement.class);
        } else {
          if (nextStatement == null) return;
          if (elseStatement != null) return;
          PsiStatement next = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
          if (!(equivalence.statementsAreEquivalent(expectedAfterStatement, next) ||
                next == null && expectedAfterStatement instanceof PsiReturnStatement &&
                equivalence.statementsAreEquivalent(expectedAfterStatement, ControlFlowUtils.getNextReturnStatement(nextStatement)))) {
            return;
          }
          forEach = ObjectUtils.tryCast(nextStatement, PsiForeachStatement.class);
        }
        if (forEach != null && equivalence.expressionsAreEquivalent(forEach.getIteratedValue(), arrayExpression)) {
          holder.problem(condition, InspectionGadgetsBundle.message("inspection.redundant.length.check.display.name")).fix(new SimplifyBooleanExpressionFix(condition, false)).register();
        }
      }
    };
  }

  @Nullable
  public static PsiExpression getValueComparedWithZero(@NotNull PsiBinaryExpression binOp) {
    PsiExpression rOperand = binOp.getROperand();
    if (rOperand == null) return null;
    PsiExpression lOperand = binOp.getLOperand();
    if (ExpressionUtils.isZero(lOperand)) return rOperand;
    if (ExpressionUtils.isZero(rOperand)) return lOperand;
    return null;
  }
}
