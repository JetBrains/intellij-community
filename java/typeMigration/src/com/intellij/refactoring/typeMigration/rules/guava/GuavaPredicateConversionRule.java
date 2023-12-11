// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class GuavaPredicateConversionRule extends GuavaLambdaConversionRule {
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

  public static boolean isEnclosingCallAPredicate(@NotNull PsiMethodCallExpression enclosingMethodCallExpression) {
    return isPredicates(enclosingMethodCallExpression);
  }

  private static int getExpressionPosition(@NotNull PsiExpressionList expressionList, @NotNull PsiExpression expression) {
    var expressions = expressionList.getExpressions();
    for (int i = 0; i < expressions.length; ++i) {
      if (expressions[i].equals(expression)) {
        return i;
      }
    }
    return -1;
  }

  public static boolean isPredicateConvertibleInsideEnclosingMethod(@NotNull PsiMethodCallExpression innerMethodCallExpression,
                                                                    @NotNull PsiMethodCallExpression enclosingMethodCallExpression) {
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(innerMethodCallExpression, PsiExpressionList.class);
    if (expressionList == null) return false;
    PsiMethod enclosingMethod = enclosingMethodCallExpression.resolveMethod();
    if (enclosingMethod == null) return false;
    PsiClass aClass = enclosingMethod.getContainingClass();
    if (aClass == null) return false;
    int position = getExpressionPosition(expressionList, innerMethodCallExpression);
    return Arrays.stream(aClass.findMethodsByName(enclosingMethod.getName(), true))
      .filter(method -> PsiUtil.isMemberAccessibleAt(method, innerMethodCallExpression))
      .anyMatch(method -> areExpressionsConvertibleToMethodParameters(expressionList.getExpressionTypes(),
                                                                      method.getParameterList().getParameters(),
                                                                      position));
  }

  private static boolean areExpressionsConvertibleToMethodParameters(PsiType[] expressionTypes,
                                                                     PsiParameter[] parameters,
                                                                     int predicateExpressionPosition) {
    if (parameters.length == 0 || (parameters.length > expressionTypes.length && !parameters[expressionTypes.length].isVarArgs())) {
      return false;
    }

    boolean isJavaPredicatePresented = false;
    int parametersLastIndex = parameters.length - 1;
    for (int expressionTypeIndex = 0; expressionTypeIndex < expressionTypes.length; ++expressionTypeIndex) {
      PsiType parameterType;
      if (expressionTypeIndex < parameters.length) {
        if (parameters[expressionTypeIndex].isVarArgs()) {
          parameterType = ((PsiArrayType)parameters[expressionTypeIndex].getType()).getComponentType();
        }
        else {
          parameterType = parameters[expressionTypeIndex].getType();
        }
      }
      else {
        if (!parameters[parametersLastIndex].isVarArgs()) return false;
        parameterType = ((PsiArrayType)parameters[parametersLastIndex].getType()).getComponentType();
      }
      if (!parameterType.isConvertibleFrom(expressionTypes[expressionTypeIndex])) return false;
      PsiClass parameterClass = PsiTypesUtil.getPsiClass(parameterType);
      if (parameterClass == null) continue;
      String qualifiedClassName = parameterClass.getQualifiedName();
      if (qualifiedClassName == null) continue;

      if (expressionTypeIndex == predicateExpressionPosition && qualifiedClassName.equals(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE)) {
        isJavaPredicatePresented = true;
      }
    }
    return isJavaPredicatePresented;
  }
}
