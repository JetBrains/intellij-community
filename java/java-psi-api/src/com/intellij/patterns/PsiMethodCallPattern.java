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

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PsiMethodCallPattern extends PsiExpressionPattern<PsiMethodCallExpression, PsiMethodCallPattern> {
  PsiMethodCallPattern() {
    super(PsiMethodCallExpression.class);
  }

  public PsiMethodCallPattern withArguments(final ElementPattern<? extends PsiExpression>... arguments) {
    return with(new PatternCondition<PsiMethodCallExpression>("withArguments") {
      @Override
      public boolean accepts(@NotNull PsiMethodCallExpression callExpression, ProcessingContext context) {
        final PsiExpression[] actualArguments = callExpression.getArgumentList().getExpressions();
        if (arguments.length != actualArguments.length) {
          return false;
        }
        for (int i = 0; i < actualArguments.length; i++) {
          if (!arguments[i].accepts(actualArguments[i], context)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public PsiMethodCallPattern withQualifier(final ElementPattern<? extends PsiExpression> qualifier) {
    return with(new PatternCondition<PsiMethodCallExpression>("withQualifier") {
      @Override
      public boolean accepts(@NotNull PsiMethodCallExpression psiMethodCallExpression, ProcessingContext context) {
        return qualifier.accepts(psiMethodCallExpression.getMethodExpression().getQualifierExpression(), context);
      }
    });
  }
}
