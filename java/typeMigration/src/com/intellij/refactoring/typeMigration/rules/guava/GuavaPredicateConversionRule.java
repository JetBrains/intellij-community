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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
/**
 * @author Dmitry Batkovich
 */
public class GuavaPredicateConversionRule extends GuavaLambdaConversionRule {
  private static final String GUAVA_PREDICATES_UTILITY = "com.google.common.base.Predicates";

  protected GuavaPredicateConversionRule() {
    super(GuavaLambda.PREDICATE);
  }

  @NotNull
  @Override
  protected Set<String> getAdditionalUtilityClasses() {
    return Collections.singleton(GUAVA_PREDICATES_UTILITY);
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(PsiType from,
                                                                 PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    if (!(context instanceof PsiMethodCallExpression)) {
      return null;
    }
    if (isPredicates((PsiMethodCallExpression)context)) {
      final TypeConversionDescriptorBase descriptor = GuavaPredicatesUtil.tryConvertIfPredicates(method, context);
      if (descriptor != null) {
        return descriptor;
      }
    }
    return new TypeConversionDescriptorBase() {
      @Override
      public PsiExpression replace(PsiExpression expression, TypeEvaluator evaluator) throws IncorrectOperationException {
        return (PsiExpression)expression.replace(JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText() + "::apply", expression));
      }
    };
  }

  public static boolean isPredicates(PsiMethodCallExpression expression) {
    final String methodName = expression.getMethodExpression().getReferenceName();
    if (GuavaPredicatesUtil.PREDICATES_METHOD_NAMES.contains(methodName)) {
      final PsiMethod method = expression.resolveMethod();
      if (method == null) return false;
      final PsiClass aClass = method.getContainingClass();
      if (aClass != null && GUAVA_PREDICATES_UTILITY.equals(aClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }
}
