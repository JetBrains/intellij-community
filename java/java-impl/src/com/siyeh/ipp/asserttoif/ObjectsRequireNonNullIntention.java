// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.asserttoif;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ObjectsRequireNonNullIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("objects.require.non.null.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("objects.require.non.null.intention.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new NullCheckedAssignmentPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiReferenceExpression referenceExpression)) {
      return;
    }
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable variable)) {
      return;
    }
    NullableNotNullManager manager = NullableNotNullManager.getInstance(element.getProject());
    final NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(variable);
    final PsiAnnotation annotation = info == null ? null : info.getAnnotation();
    final CommentTracker commentTracker = new CommentTracker();
    if (annotation == null) {
      final PsiStatement referenceStatement = PsiTreeUtil.getParentOfType(referenceExpression, PsiStatement.class);
      if (referenceStatement == null) {
        return;
      }
      final PsiElement parent = referenceStatement.getParent();
      if (!(parent instanceof PsiCodeBlock codeBlock)) {
        return;
      }
      final PsiStatement[] statements = codeBlock.getStatements();
      PsiStatement statementToDelete = null;
      for (PsiStatement statement : statements) {
        if (statement == referenceStatement) {
          break;
        }
        if (NullCheckedAssignmentPredicate.isNotNullAssertion(statement, variable) ||
            NullCheckedAssignmentPredicate.isIfStatementNullCheck(statement, variable)) {
          statementToDelete = statement;
          break;
        }
      }
      if (statementToDelete == null) {
        return;
      }
      commentTracker.delete(statementToDelete);
    }
    PsiReplacementUtil.replaceExpressionAndShorten(referenceExpression,
                                                   "java.util.Objects.requireNonNull(" + commentTracker.text(referenceExpression) + ")",
                                                   commentTracker);
  }

  private static class NullCheckedAssignmentPredicate implements PsiElementPredicate {

    @Override
    public boolean satisfiedBy(PsiElement element) {
      if (!PsiUtil.isLanguageLevel7OrHigher(element)) {
        return false;
      }
      if (!(element instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      if (PsiUtil.isAccessedForWriting(referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable variable)) {
        return false;
      }
      if (ClassUtils.findClass("java.util.Objects", element) == null) {
        return false;
      }
      final NullabilityAnnotationInfo info =
        NullableNotNullManager.getInstance(variable.getProject()).findEffectiveNullabilityInfo(variable);
      if (info != null && info.getNullability() == Nullability.NOT_NULL && !info.isExternal() && !info.isInferred()) {
        return true;
      }
      final PsiStatement referenceStatement = PsiTreeUtil.getParentOfType(referenceExpression, PsiStatement.class);
      final PsiElement parent = referenceStatement != null ? referenceStatement.getParent() : null;
      if (!(parent instanceof PsiCodeBlock codeBlock)) {
        return false;
      }
      final PsiStatement[] statements = codeBlock.getStatements();
      for (PsiStatement statement : statements) {
        if (statement == referenceStatement) {
          return false;
        }
        if (isNotNullAssertion(statement, variable) || isIfStatementNullCheck(statement, variable)) {
          return true;
        }
      }
      return false;
    }

    static boolean isIfStatementNullCheck(PsiStatement statement, @NotNull PsiVariable variable) {
      if (!(statement instanceof PsiIfStatement ifStatement)) {
        return false;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null) {
        return false;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (!isSimpleThrowStatement(thenBranch)) {
        return false;
      }
      final PsiExpression condition = ifStatement.getCondition();
      return ComparisonUtils.isNullComparison(condition, variable, true);
    }

    static boolean isNotNullAssertion(PsiStatement statement, @NotNull PsiVariable variable) {
      if (!(statement instanceof PsiAssertStatement assertStatement)) {
        return false;
      }
      final PsiExpression condition = assertStatement.getAssertCondition();
      return ComparisonUtils.isNullComparison(condition, variable, false);
    }

    public static boolean isSimpleThrowStatement(PsiStatement element) {
      if (element instanceof PsiThrowStatement) {
        return true;
      }
      else if (element instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length != 1) {
          return false;
        }
        final PsiStatement statement = statements[0];
        return isSimpleThrowStatement(statement);
      }
      return false;
    }
  }
}
