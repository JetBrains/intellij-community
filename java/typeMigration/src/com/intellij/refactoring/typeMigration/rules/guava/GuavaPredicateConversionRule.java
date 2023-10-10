// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

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
      public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
        final PsiExpression methodReference =
          JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText() + "::test", expression);
        return (PsiExpression)expression.replace(methodReference);
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

  public static boolean isEnclosingCallPredicate(PsiMethodCallExpression expression) {
    PsiMethodCallExpression enclosingMethodCallExpression =
      PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
    if (enclosingMethodCallExpression == null) return false;
    return isPredicates(enclosingMethodCallExpression);
  }


  public static boolean isPredicateConvertibleInsideEnclosingMethod(PsiMethodCallExpression methodCallExpression) {
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(methodCallExpression, PsiExpressionList.class);
    PsiMethodCallExpression enclosingMethodCallExpression =
      PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethodCallExpression.class);
    if (expressionList == null || enclosingMethodCallExpression == null) return true;
    PsiMethod enclosingMethod = enclosingMethodCallExpression.resolveMethod();
    if (enclosingMethod == null) return true;
    PsiClass aClass = enclosingMethod.getContainingClass();
    if (aClass == null) return true;
    int position = ExpressionUtils.getExpressionPosition(expressionList, methodCallExpression);
    return Arrays.stream(aClass.findMethodsByName(enclosingMethod.getName(), true))
      .filter(method -> PsiUtil.isMemberAccessibleAt(method, methodCallExpression))
      .anyMatch(method -> areExpressionsConvertibleToMethodParameters(expressionList.getExpressionTypes(),
                                                                      method.getParameterList().getParameters(),
                                                                      position));
  }

  private static boolean areExpressionsConvertibleToMethodParameters(PsiType[] expressionTypes,
                                                                     PsiParameter[] parameters,
                                                                     int predicateExpressionPosition) {
    if (parameters.length != expressionTypes.length) {
      return false;
    }

    boolean isJavaPredicatePresented = false;
    for (int i = 0; i < parameters.length; ++i) {
      PsiType parameterType = Objects.requireNonNull(parameters[i].getType());
      if (!parameterType.isConvertibleFrom(expressionTypes[i])) return false;
      PsiClass parameterClass = PsiTypesUtil.getPsiClass(parameterType);
      if (parameterClass == null) continue;
      String qualifiedClassName = parameterClass.getQualifiedName();
      if (qualifiedClassName == null) continue;

      if (i == predicateExpressionPosition && qualifiedClassName.equals("java.util.function.Predicate")) {
        isJavaPredicatePresented = true;
      }
    }
    return isJavaPredicatePresented;
  }
}
