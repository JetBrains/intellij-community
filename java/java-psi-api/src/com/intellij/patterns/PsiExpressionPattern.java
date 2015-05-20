/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.patterns;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiExpressionPattern<T extends PsiExpression, Self extends PsiExpressionPattern<T,Self>> extends PsiJavaElementPattern<T,Self> {
  protected PsiExpressionPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self ofType(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>("ofType") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return pattern.accepts(t.getType(), context);
      }
    });
  }

  public PsiMethodCallPattern methodCall(final ElementPattern<? extends PsiMethod> method) {
    final PsiNamePatternCondition nameCondition = ContainerUtil.findInstance(method.getCondition().getConditions(), PsiNamePatternCondition.class);
    return new PsiMethodCallPattern().and(this).with(new PatternCondition<PsiMethodCallExpression>("methodCall") {
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