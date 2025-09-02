// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInspection.util.OptionalRefactoringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

final class FindFirstMigration extends BaseStreamApiMigration {
  FindFirstMigration(boolean shouldWarn) {
    super(shouldWarn, "findFirst");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiStatement statement = tb.getSingleStatement();
    PsiStatement loopStatement = tb.getStreamSourceStatement();
    CommentTracker ct = new CommentTracker();
    if (statement instanceof PsiReturnStatement returnStatement) {
      PsiExpression value = returnStatement.getReturnValue();
      if (value == null) return null;
      PsiReturnStatement nextReturnStatement = ControlFlowUtils.getNextReturnStatement(loopStatement);
      if (nextReturnStatement == null) return null;
      PsiExpression orElseExpression = nextReturnStatement.getReturnValue();
      if (!ExpressionUtils.isSafelyRecomputableExpression(orElseExpression)) return null;
      String stream = generateOptionalUnwrap(ct, tb, value, orElseExpression, PsiTypesUtil.getMethodReturnType(returnStatement));
      boolean sibling = nextReturnStatement.getParent() == loopStatement.getParent();
      PsiElement replacement = ct.replaceAndRestoreComments(loopStatement, "return " + stream + ";");
      if(sibling || !ControlFlowUtils.isReachable(nextReturnStatement)) {
        new CommentTracker().deleteAndRestoreComments(nextReturnStatement);
      }
      return replacement;
    }
    else {
      PsiStatement[] statements = tb.getStatements();
      if (statements.length != 2) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null ||
          isQualified(assignment.getLExpression()) ||
          (tb.getVariable().getType() instanceof PsiPrimitiveType &&
           !ExpressionUtils.isReferenceTo(assignment.getRExpression(), tb.getVariable()))) {
        // if we found an assignment with primitive stream variable, then we are not assigning to local variable
        // (see StreamApiMigrationInspection#findMigrationForBreak), thus it could be handled via ifPresent()
        if(!(statements[0] instanceof PsiExpressionStatement)) return null;
        PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        return ct.replaceAndRestoreComments(
          loopStatement, tb.generate(ct) + ".findFirst().ifPresent(" + ct.lambdaText(tb.getVariable(), expression) + ");");
      }
      PsiReferenceExpression lValue = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (lValue == null) return null;
      PsiVariable var = tryCast(lValue.resolve(), PsiVariable.class);
      if (var == null) return null;
      PsiExpression value = assignment.getRExpression();
      if (value == null) return null;
      InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
      PsiExpression initializer = var.getInitializer();
      PsiExpression falseExpression = lValue;
      PsiElement toDelete = null;
      if (status != ControlFlowUtils.InitializerUsageStatus.UNKNOWN &&
          (status != ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE || ExpressionUtils.isSafelyRecomputableExpression(initializer))) {
        falseExpression = initializer;
      } else {
        PsiElement maybeAssignment = PsiTreeUtil.skipWhitespacesAndCommentsBackward(loopStatement);
        PsiExpression prevRValue = ExpressionUtils.getAssignmentTo(maybeAssignment, var);
        if (prevRValue != null) {
          toDelete = maybeAssignment;
          falseExpression = prevRValue;
        }
      }
      String replacementText = generateOptionalUnwrap(ct, tb, value, falseExpression, var.getType());
      if (toDelete != null) {
        ct.delete(toDelete);
      }
      return replaceInitializer(loopStatement, var, initializer, replacementText, status, ct);
    }
  }

  private static boolean isQualified(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return expression instanceof PsiReferenceExpression && !ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)expression) ||
           expression instanceof PsiArrayAccessExpression;
  }

  private static String generateOptionalUnwrap(CommentTracker ct, TerminalBlock tb,
                                               PsiExpression trueExpression, PsiExpression falseExpression,
                                               PsiType targetType) {
    String qualifier = tb.generate(ct) + ".findFirst()";
    return OptionalRefactoringUtil.generateOptionalUnwrap(
      qualifier, tb.getVariable(), ct.markUnchanged(trueExpression), ct.markUnchanged(falseExpression), targetType, false);
  }
}
