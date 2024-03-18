// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoopWithImplicitTerminationConditionInspection
  extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (Boolean.TRUE.equals(infos[0])) {
      return InspectionGadgetsBundle.message(
        "loop.with.implicit.termination.condition.dowhile.problem.descriptor");
    }
    return InspectionGadgetsBundle.message(
      "loop.with.implicit.termination.condition.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new LoopWithImplicitTerminationConditionFix();
  }

  private static class LoopWithImplicitTerminationConditionFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "loop.with.implicit.termination.condition.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiConditionalLoopStatement parent = ObjectUtils.tryCast(element.getParent(), PsiConditionalLoopStatement.class);
      if (parent == null) return;
      final PsiExpression loopCondition = parent.getCondition();
      if (loopCondition == null) return;
      final PsiStatement body = parent.getBody();
      final boolean firstStatement = !(parent instanceof PsiDoWhileStatement);
      final PsiStatement statement;
      if (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 0) {
          return;
        }
        if (firstStatement) {
          statement = statements[0];
        }
        else {
          statement = statements[statements.length - 1];
        }
      }
      else {
        statement = body;
      }
      if (!(statement instanceof PsiIfStatement ifStatement)) {
        return;
      }
      final PsiExpression ifCondition = ifStatement.getCondition();
      if (ifCondition == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (containsUnlabeledBreakStatement(thenBranch)) {
        CommentTracker commentTracker = new CommentTracker();
        final String negatedExpressionText =
          BoolUtils.getNegatedExpressionText(ifCondition, commentTracker);
        PsiReplacementUtil.replaceExpression(loopCondition, negatedExpressionText, commentTracker);
        replaceStatement(ifStatement, elseBranch);
      }
      else if (containsUnlabeledBreakStatement(elseBranch)) {
        loopCondition.replace(ifCondition);
        if (thenBranch == null) {
          ifStatement.delete();
        }
        else {
          replaceStatement(ifStatement, thenBranch);
        }
      }
    }

    private static void replaceStatement(@NotNull PsiStatement replacedStatement, @Nullable PsiStatement replacingStatement) {
      if (replacingStatement == null) {
        replacedStatement.delete();
        return;
      }
      if (!(replacingStatement instanceof PsiBlockStatement blockStatement)) {
        replacedStatement.replace(replacingStatement);
        return;
      }
      final PsiCodeBlock codeBlock =
        blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        final PsiElement receiver = replacedStatement.getParent();
        for (int i = children.length - 2; i > 0; i--) {
          final PsiElement child = children[i];
          if (child instanceof PsiWhiteSpace) {
            continue;
          }
          receiver.addAfter(child, replacedStatement);
        }
        replacedStatement.delete();
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoopWithImplicitTerminationConditionVisitor();
  }

  private static class LoopWithImplicitTerminationConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      check(statement, false);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      check(statement, true);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      check(statement, false);
    }

    private void check(PsiConditionalLoopStatement statement, boolean doWhile) {
      final PsiExpression condition = statement.getCondition();
      if (!BoolUtils.isTrue(condition)) {
        return;
      }
      if (isLoopWithImplicitTerminationCondition(statement, !doWhile)) {
        return;
      }
      registerStatementError(statement, doWhile);
    }

    private static boolean isLoopWithImplicitTerminationCondition(PsiLoopStatement statement, boolean firstStatement) {
      final PsiStatement body = statement.getBody();
      final PsiStatement bodyStatement;
      if (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 0) {
          return true;
        }
        if (firstStatement) {
          bodyStatement = statements[0];
        }
        else {
          bodyStatement = statements[statements.length - 1];
        }
      }
      else {
        bodyStatement = body;
      }
      return !isImplicitTerminationCondition(bodyStatement);
    }

    private static boolean isImplicitTerminationCondition(@Nullable PsiStatement statement) {
      if (!(statement instanceof PsiIfStatement ifStatement)) {
        return false;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (containsUnlabeledBreakStatement(thenBranch)) {
        return true;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return containsUnlabeledBreakStatement(elseBranch);
    }
  }

  static boolean containsUnlabeledBreakStatement(@Nullable PsiStatement statement) {
    if (!(statement instanceof PsiBlockStatement blockStatement)) {
      return isUnlabeledBreakStatement(statement);
    }
    final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
    final PsiStatement firstStatement = ControlFlowUtils.getOnlyStatementInBlock(codeBlock);
    return isUnlabeledBreakStatement(firstStatement);
  }

  private static boolean isUnlabeledBreakStatement(
    @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiBreakStatement breakStatement)) {
      return false;
    }
    final PsiIdentifier identifier =
      breakStatement.getLabelIdentifier();
    return identifier == null;
  }
}