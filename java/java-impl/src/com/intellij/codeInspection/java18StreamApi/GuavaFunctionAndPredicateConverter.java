/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFunctionAndPredicateConverter {
  private final static Logger LOG = Logger.getInstance(GuavaFunctionAndPredicateConverter.class);

  @Nullable
  public static Boolean isClassConditionPredicate(final PsiExpression expression) {
    final PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (InheritanceUtil.isInheritor(resolvedClass, "com.google.common.base.Function") ||
          InheritanceUtil.isInheritor(resolvedClass, "com.google.common.base.Predicate")) {
        return Boolean.FALSE;
      }
      else if (InheritanceUtil.isInheritor(resolvedClass, CommonClassNames.JAVA_LANG_CLASS)) {
        return Boolean.TRUE;
      }
    }
    return null;
  }

  @NotNull
  public static String convertFunctionOrPredicateParameter(final @NotNull PsiExpression expression,
                                                           final boolean role) {
    if (role) {
      final String pattern = expression instanceof PsiMethodCallExpression || expression instanceof PsiReferenceExpression
                             ? "%s::isInstance"
                             : "(%s)::isInstance";
      return String.format(pattern, expression.getText());
    }
    if (expression instanceof PsiNewExpression) {
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)expression).getAnonymousClass();
      if (anonymousClass != null && AnonymousCanBeLambdaInspection.canBeConvertedToLambda(anonymousClass, true)) {
        final PsiLambdaExpression lambdaExpression = AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(expression, true);
        LOG.assertTrue(lambdaExpression != null);
        return lambdaExpression.getText();
      }
    }
    String qualifierText = expression.getText();
    if (!(expression instanceof PsiMethodCallExpression) && !(expression instanceof PsiReferenceExpression)) {
      qualifierText = "(" + qualifierText + ")";
    }
    return qualifierText + "::apply";
  }
}
