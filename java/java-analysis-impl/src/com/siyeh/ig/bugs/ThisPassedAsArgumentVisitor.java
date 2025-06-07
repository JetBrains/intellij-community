// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ThisPassedAsArgumentVisitor extends JavaRecursiveElementWalkingVisitor {
  private boolean passed;

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!passed) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
    if (passed) {
      return;
    }
    super.visitMethodCallExpression(call);
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      if (isThisExpression(argument)) {
        passed = true;
        break;
      }
    }
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
    if (passed) {
      return;
    }
    super.visitNewExpression(newExpression);
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      if (isThisExpression(argument)) {
        passed = true;
        break;
      }
    }
  }

  private static boolean isThisExpression(PsiExpression expression) {
    if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      return isThisExpression(parenthesizedExpression.getExpression());
    }
    return expression instanceof PsiThisExpression;
  }

  public boolean isPassed() {
    return passed;
  }
}