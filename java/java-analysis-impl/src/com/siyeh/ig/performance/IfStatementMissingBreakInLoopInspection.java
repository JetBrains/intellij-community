// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public final class IfStatementMissingBreakInLoopInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inspection.if.statement.missing.break.in.loop.description");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfStatementMissingBreakInLoopVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new IfStatementMissingBreakInLoopFix();
  }

  private static class IfStatementMissingBreakInLoopVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      PsiStatement body = statement.getBody();
      if (body == null) return;
      PsiParameter parameter = statement.getIterationParameter();
      Set<PsiVariable> nonFinalVariables = new HashSet<>();
      nonFinalVariables.add(parameter);
      Set<PsiVariable> declaredVariables = new HashSet<>();
      declaredVariables.add(parameter);
      visitLoopBody(body, nonFinalVariables, declaredVariables);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      PsiStatement body = statement.getBody();
      if (body == null) return;
      Set<PsiVariable> nonFinalVariables = new HashSet<>();
      Set<PsiVariable> declaredVariables = new HashSet<>();
      PsiDeclarationStatement initialization = tryCast(statement.getInitialization(), PsiDeclarationStatement.class);
      if (initialization != null) collectVariables(initialization, statement, nonFinalVariables, declaredVariables);
      PsiStatement update = statement.getUpdate();
      if (update != null && mayHaveOutsideOfLoopSideEffects(update, declaredVariables)) return;
      PsiExpression condition = statement.getCondition();
      if (condition != null && mayHaveOutsideOfLoopSideEffects(condition, declaredVariables)) return;
      visitLoopBody(body, nonFinalVariables, declaredVariables);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      visitLoopStatement(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      visitLoopStatement(statement);
    }

    private void visitLoopStatement(@NotNull PsiConditionalLoopStatement loopStatement) {
      PsiStatement body = loopStatement.getBody();
      if (body == null) return;
      Set<PsiVariable> nonFinalVariables = new HashSet<>();
      Set<PsiVariable> declaredVariables = new HashSet<>();
      PsiExpression condition = loopStatement.getCondition();
      if (condition != null) {
        if (mayHaveOutsideOfLoopSideEffects(condition, declaredVariables)) return;
        Set<PsiVariable> conditionVariables = VariableAccessUtils.collectUsedVariables(condition);
        declaredVariables.addAll(conditionVariables);
        nonFinalVariables.addAll(conditionVariables);
      }
      visitLoopBody(body, nonFinalVariables, declaredVariables);
    }

    private void visitLoopBody(@NotNull PsiStatement loopBody,
                               @NotNull Set<PsiVariable> nonFinalVariables,
                               @NotNull Set<PsiVariable> declaredVariables) {
      PsiStatement[] statements = getStatements(loopBody);
      if (!hasMissingBreakCandidates(statements)) return;
      PsiIfStatement ifStatementMissingBreak = null;
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiDeclarationStatement) {
          collectVariables((PsiDeclarationStatement)statement, loopBody, nonFinalVariables, declaredVariables);
        }
        else if (statement instanceof PsiIfStatement ifStatement) {
          PsiExpression condition = ifStatement.getCondition();
          if (condition == null || mayHaveOutsideOfLoopSideEffects(condition, declaredVariables)) {
            return;
          }
          if (isMissingBreak(ifStatement, nonFinalVariables, declaredVariables)) {
            if (ifStatementMissingBreak != null) return;
            ifStatementMissingBreak = ifStatement;
            continue;
          }
        }
        if (mayHaveOutsideOfLoopSideEffects(statement, declaredVariables)) return;
      }
      if (ifStatementMissingBreak == null) return;
      registerError(ifStatementMissingBreak.getFirstChild());
    }

    private static boolean hasMissingBreakCandidates(PsiStatement[] statements) {
      return Arrays.stream(statements)
        .filter(s -> s instanceof PsiIfStatement)
        .map(s -> getStatements((PsiIfStatement)s))
        .filter(ss -> ss.length != 0)
        .anyMatch(ss -> Arrays.stream(ss).allMatch(s -> getAssignment(s) != null));
    }

    private static PsiStatement @NotNull [] getStatements(@NotNull PsiIfStatement ifStatement) {
      if (ifStatement.getElseBranch() != null) return PsiStatement.EMPTY_ARRAY;
      PsiStatement branch = ifStatement.getThenBranch();
      if (branch == null) return PsiStatement.EMPTY_ARRAY;
      return getStatements(branch);
    }

    @Nullable
    private static PsiAssignmentExpression getAssignment(@NotNull PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement)) return null;
      PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
      if (!(expression instanceof PsiAssignmentExpression assignment)) return null;
      if (!JavaTokenType.EQ.equals(assignment.getOperationTokenType())) return null;
      return assignment;
    }

    private static boolean isMissingBreak(@NotNull PsiIfStatement ifStatement,
                                          @NotNull Set<PsiVariable> nonFinalVariables,
                                          @NotNull Set<PsiVariable> declaredVariables) {
      PsiStatement[] statements = getStatements(ifStatement);
      if (statements.length == 0) return false;
      Set<PsiVariable> usedVariables = new HashSet<>();
      for (PsiStatement statement : statements) {
        PsiAssignmentExpression assignment = getAssignment(statement);
        if (assignment == null) return false;
        PsiExpression lhs = assignment.getLExpression();
        Set<PsiVariable> lhsVariables = VariableAccessUtils.collectUsedVariables(lhs);
        if (haveCommonElements(lhsVariables, nonFinalVariables) || mayHaveOutsideOfLoopSideEffects(lhs, declaredVariables)) return false;
        PsiExpression rhs = assignment.getRExpression();
        if (rhs == null) return false;
        Set<PsiVariable> rhsVariables = VariableAccessUtils.collectUsedVariables(rhs);
        if (haveCommonElements(rhsVariables, nonFinalVariables) || mayHaveOutsideOfLoopSideEffects(rhs, declaredVariables)) return false;
        usedVariables.addAll(rhsVariables);
        if (!usedVariables.addAll(lhsVariables)) return false;
      }
      return true;
    }

    private static boolean haveCommonElements(Set<PsiVariable> s1, Set<PsiVariable> s2) {
      return !Collections.disjoint(s2, s1);
    }

    private static void collectVariables(@NotNull PsiDeclarationStatement declaration,
                                         @NotNull PsiStatement scope,
                                         @NotNull Set<PsiVariable> nonFinalVariables,
                                         @NotNull Set<? super PsiVariable> declaredVariables) {
      Set<PsiVariable> usedVariables = VariableAccessUtils.collectUsedVariables(declaration);
      boolean hasNonFinalVariables = haveCommonElements(usedVariables, nonFinalVariables);
      for (PsiElement element : declaration.getDeclaredElements()) {
        if (!(element instanceof PsiVariable variable)) continue;
        declaredVariables.add(variable);
        if (hasNonFinalVariables || !HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null)) {
          nonFinalVariables.add(variable);
        }
      }
    }

    private static PsiStatement @NotNull [] getStatements(@NotNull PsiStatement statement) {
      if (statement instanceof PsiBlockStatement) return ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      return new PsiStatement[]{statement};
    }

    /**
     * Returns true if element execution may cause side-effect outside of the loop. Break/continue or update of a variable defined inside of
     * the loop are considered to be loop only side-effects.
     *
     * @param element           element to check
     * @param declaredVariables variables declared inside of the loop
     */
    private static boolean mayHaveOutsideOfLoopSideEffects(@NotNull PsiElement element, @NotNull Set<PsiVariable> declaredVariables) {
      return SideEffectChecker.mayHaveSideEffects(element, e -> isLoopOnlySideEffect(e, declaredVariables));
    }

    private static boolean isLoopOnlySideEffect(PsiElement e, @NotNull Set<PsiVariable> declaredVariables) {
      if (e instanceof PsiContinueStatement || e instanceof PsiBreakStatement || e instanceof PsiVariable) {
        return true;
      }

      PsiExpression operand = null;
      if (e instanceof PsiUnaryExpression) {
        operand = ((PsiUnaryExpression)e).getOperand();
      }
      else if (e instanceof PsiAssignmentExpression) {
        operand = ((PsiAssignmentExpression)e).getLExpression();
      }
      if (operand == null) return false;
      operand = PsiUtil.skipParenthesizedExprDown(operand);
      PsiReferenceExpression ref = tryCast(operand, PsiReferenceExpression.class);
      if (ref != null) return isDeclaredVariable(ref.resolve(), declaredVariables);
      PsiArrayAccessExpression arrayAccess = tryCast(operand, PsiArrayAccessExpression.class);
      if (arrayAccess == null) return true;
      return loopOnlyVariablesChanged(arrayAccess, declaredVariables);
    }

    private static boolean loopOnlyVariablesChanged(@NotNull PsiArrayAccessExpression arrayAccess,
                                                    @NotNull Set<PsiVariable> declaredVariables) {
      return ExpressionUtils.nonStructuralChildren(arrayAccess.getArrayExpression()).allMatch(child -> {
        PsiArrayAccessExpression childArrayAccess = tryCast(child, PsiArrayAccessExpression.class);
        if (childArrayAccess != null) return loopOnlyVariablesChanged(childArrayAccess, declaredVariables);
        PsiReferenceExpression childRef = tryCast(child, PsiReferenceExpression.class);
        if (childRef != null) return isDeclaredVariable(childRef.resolve(), declaredVariables);
        return true;
      });
    }

    private static boolean isDeclaredVariable(PsiElement element, Set<PsiVariable> declaredVariables) {
      PsiVariable variable = tryCast(element, PsiVariable.class);
      return variable == null || declaredVariables.contains(variable);
    }
  }

  private static class IfStatementMissingBreakInLoopFix extends PsiUpdateModCommandQuickFix {

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiIfStatement ifStatement = tryCast(startElement.getParent(), PsiIfStatement.class);
      if (ifStatement == null || ifStatement.getElseBranch() != null) return;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) return;
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      PsiStatement breakStatement = factory.createStatementFromText("break;", null);
      PsiCodeBlock block = getBlock(thenBranch);
      if (block == null) return;
      block.addBefore(breakStatement, block.getLastChild());
      CodeStyleManager.getInstance(project).reformat(ifStatement);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.if.statement.missing.break.in.loop.quickfix");
    }

    @Nullable
    private static PsiCodeBlock getBlock(@NotNull PsiStatement thenBranch) {
      if (thenBranch instanceof PsiBlockStatement) return ((PsiBlockStatement)thenBranch).getCodeBlock();
      PsiStatement statementInBlock = BlockUtils.expandSingleStatementToBlockStatement(thenBranch);
      return tryCast(statementInBlock.getParent(), PsiCodeBlock.class);
    }
  }
}
