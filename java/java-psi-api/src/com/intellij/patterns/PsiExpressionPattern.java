// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.patterns;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionPattern<T extends PsiExpression, Self extends PsiExpressionPattern<T,Self>> extends PsiJavaElementPattern<T,Self> {
  protected PsiExpressionPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self ofType(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>("ofType") {
      @Override
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return pattern.accepts(t.getType(), context);
      }
    });
  }

  public PsiMethodCallPattern methodCall(final ElementPattern<? extends PsiMethod> method) {
    final PsiNamePatternCondition nameCondition = ContainerUtil.findInstance(method.getCondition().getConditions(), PsiNamePatternCondition.class);
    return new PsiMethodCallPattern().and(this).with(new PatternCondition<PsiMethodCallExpression>("methodCall") {
      @Override
      public boolean accepts(@NotNull PsiMethodCallExpression callExpression, ProcessingContext context) {
        PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        if (nameCondition != null && !nameCondition.getNamePattern().accepts(methodExpression.getReferenceName())) {
          return false;
        }

        for (JavaResolveResult result : methodExpression.multiResolve(true)) {
          if (method.accepts(result.getElement(), context)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public Self skipParentheses(final ElementPattern<? extends PsiExpression> expressionPattern) {
    return with(new PatternCondition<T>("skipParentheses") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        PsiExpression expression = t;
        while (expression instanceof PsiParenthesizedExpression) {
          expression = ((PsiParenthesizedExpression)expression).getExpression();
        }
        return expressionPattern.accepts(expression, context);
      }
    });
  }

  public static class Capture<T extends PsiExpression> extends PsiExpressionPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

  }
}