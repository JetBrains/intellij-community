// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class IteratorUtils {

  private IteratorUtils() {
  }

  /**
   * @param context the body of hasNext() or hasPrevious() method
   * @param target  the variable that contains an iterator. Specify
   *                null to check for "this" as target variable.
   * @return an illegal call expression, like iterator.next() or listIterator.previous()
   */
  public static @Nullable PsiMethodCallExpression getIllegalCallInHasNext(PsiElement context, PsiVariable target, boolean checkTarget) {
    final CallsIteratorNextVisitor visitor =
      new CallsIteratorNextVisitor(target, checkTarget, false);
    context.accept(visitor);
    return visitor.getIllegalCall();
  }

  private static final class CallsIteratorNextVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private static final Pattern SCANNER_PATTERN = Pattern.compile("next.*");

    private final boolean checkTarget;
    private final boolean checkScanner;
    private final PsiVariable target;
    private PsiMethodCallExpression illegalCall;

    private CallsIteratorNextVisitor(PsiVariable target, boolean checkTarget, boolean checkScanner) {
      this.checkTarget = checkTarget;
      this.target = target;
      this.checkScanner = checkScanner;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (checkScanner) {
        if (!MethodCallUtils.isCallToMethod(expression,
                                            CommonClassNames.JAVA_UTIL_ITERATOR, null,
                                            SCANNER_PATTERN)) {
          return;
        }
      }
      else {
        if (!MethodCallUtils.isCallToMethod(expression, CommonClassNames.JAVA_UTIL_ITERATOR, null, HardcodedMethodConstants.NEXT)
          && !MethodCallUtils.isCallToMethod(expression, "java.util.ListIterator", null, "previous")) {
          return;
        }
      }
      if (checkTarget) {
        final PsiReferenceExpression methodExpression =
          expression.getMethodExpression();
        final PsiExpression qualifier =
          methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression referenceExpression)) {
          if (target != null) {
            return;
          }
          if (qualifier != null &&
              !(qualifier instanceof PsiThisExpression) &&
              !(qualifier instanceof PsiSuperExpression)) {
            return;
          }
        }
        else {
          final PsiElement element = referenceExpression.resolve();
          if (target == null || !target.equals(element)) {
            return;
          }
        }
      }
      illegalCall = expression;
      stopWalking();
    }

    private @Nullable PsiMethodCallExpression getIllegalCall() {
      return illegalCall;
    }
  }
}
