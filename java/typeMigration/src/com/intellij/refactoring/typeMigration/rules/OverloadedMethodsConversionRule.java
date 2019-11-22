// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Réda Housni Alaoui
 */
public class OverloadedMethodsConversionRule extends TypeConversionRule {

  @Nullable
  @Override
  public TypeConversionDescriptorBase findConversion(
    PsiType from,
    PsiType to,
    PsiMember member,
    PsiExpression expression,
    TypeMigrationLabeler labeler) {
    if (!(expression.getContext() instanceof PsiExpressionList)) {
      return null;
    }
    PsiExpressionList expressionList = (PsiExpressionList)expression.getContext();
    if (!(expressionList.getContext() instanceof PsiMethodCallExpression)) {
      return null;
    }
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expressionList.getContext();
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return null;
    }

    if (method.isConstructor()) {
      return null;
    }
    if (method.getNameIdentifier() == null) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final int parameterCount = parameterList.getParametersCount();
    if (parameterCount == 0) {
      return null;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return null;
    }

    final String methodName = method.getName();
    final PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, false);
    if (sameNameMethods.length == 0) {
      return null;
    }

    PsiExpression[] arguments = expressionList.getExpressions();

    int migratedArgumentIndex = Arrays.asList(arguments).indexOf(expression);

    PsiType[] newArgumentTypes = new PsiType[expressionList.getExpressionCount()];
    for (int argumentIndex = 0; argumentIndex < newArgumentTypes.length; argumentIndex++) {
      if (argumentIndex == migratedArgumentIndex) {
        newArgumentTypes[argumentIndex] = to;
      }
      else {
        newArgumentTypes[argumentIndex] = labeler.getTypeEvaluator().getType(arguments[argumentIndex]);
      }
    }

    for (PsiMethod sameNameMethod : sameNameMethods) {
      if (method.equals(sameNameMethod)) {
        continue;
      }
      final PsiParameterList otherParameterList = sameNameMethod.getParameterList();
      if (parameterCount != otherParameterList.getParametersCount()) {
        continue;
      }

      if (!areModifierListsEquivalent(method.getModifierList(), sameNameMethod.getModifierList())) {
        continue;
      }

      PsiParameter[] sameNameMethodParameters = sameNameMethod.getParameterList().getParameters();
      if (areArgumentTypesAssignableToParameters(labeler.getTypeEvaluator(), newArgumentTypes,
                                                 sameNameMethodParameters)) {
        return new TypeConversionDescriptorBase();
      }
    }

    return null;
  }

  private boolean areArgumentTypesAssignableToParameters(TypeEvaluator typeEvaluator,
                                                         PsiType[] argumentTypes,
                                                         PsiParameter[] parameters) {
    for (int parameterIndex = 0; parameterIndex < parameters.length; parameterIndex++) {
      PsiParameter parameter = parameters[parameterIndex];
      PsiType parameterType = typeEvaluator.getType(parameter);
      if (parameterType == null) {
        return false;
      }
      if (parameterType instanceof PsiEllipsisType) {
        for (int argumentIndex = parameterIndex; argumentIndex < argumentTypes.length; argumentIndex++) {
          PsiType argumentType = argumentTypes[argumentIndex];
          if (!isArgumentTypeAssignableToParameterEllipsisType(argumentType, (PsiEllipsisType)parameterType)) {
            return false;
          }
        }
      }
      else {
        PsiType argumentType = argumentTypes[parameterIndex];
        if (!TypeConversionUtil.isAssignable(parameterType, argumentType)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean isArgumentTypeAssignableToParameterEllipsisType(PsiType argumentType, PsiEllipsisType parameterType) {
    PsiType parameterComponentType = parameterType.getComponentType();
    if (argumentType instanceof PsiArrayType) {
      return TypeConversionUtil
        .isAssignable(parameterComponentType, ((PsiArrayType)argumentType).getComponentType());
    }
    else {
      return TypeConversionUtil.isAssignable(parameterComponentType, argumentType);
    }
  }

  private boolean areModifierListsEquivalent(PsiModifierList firstModifierList, PsiModifierList secondModifierList) {
    for (int i = 0; i < PsiModifier.MODIFIERS.length; i++) {
      String modifier = PsiModifier.MODIFIERS[i];
      if (firstModifierList.hasModifierProperty(modifier) && !secondModifierList.hasModifierProperty(modifier)) {
        return false;
      }
      if (secondModifierList.hasModifierProperty(modifier) && !firstModifierList.hasModifierProperty(modifier)) {
        return false;
      }
    }
    return true;
  }
}
