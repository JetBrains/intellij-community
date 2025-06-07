// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class SynchronizationUtil {

  private SynchronizationUtil() {}

  public static boolean isInSynchronizedContext(PsiElement element) {
    final PsiElement context =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiSynchronizedStatement.class, PsiClass.class, PsiLambdaExpression.class);
    if (context instanceof PsiSynchronizedStatement) {
      return true;
    }
    if (context instanceof PsiMethod modifierListOwner) {
      if (modifierListOwner.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return true;
      }
    }
    if (context instanceof PsiMethod || context instanceof PsiLambdaExpression) {
      PsiAssertStatement assertStatement = findHoldsLockAssertion(context);
      return assertStatement != null && assertStatement.getTextOffset() + assertStatement.getTextLength() < element.getTextOffset();
    }
    return false;
  }

  private static PsiAssertStatement findHoldsLockAssertion(PsiElement context) {
    return CachedValuesManager.getCachedValue(context, () -> {
      HoldsLockAssertionVisitor visitor = new HoldsLockAssertionVisitor();
      context.accept(visitor);
      return CachedValueProvider.Result.create(visitor.getAssertStatement(), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  public static boolean isCallToHoldsLock(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final @NonNls String name = methodExpression.getReferenceName();
    if (!"holdsLock".equals(name)) {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, "java.lang.Thread");
  }

  private static class HoldsLockAssertionVisitor extends JavaRecursiveElementWalkingVisitor {
    private PsiAssertStatement myAssertStatement = null;

    @Override
    public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
      if (myAssertStatement != null) return;
      super.visitAssertStatement(statement);
      final PsiExpression condition = statement.getAssertCondition();
      if (isCallToHoldsLock(condition)) {
        myAssertStatement = statement;
        stopWalking();
      }
    }

    public PsiAssertStatement getAssertStatement() {
      return myAssertStatement;
    }
  }
}