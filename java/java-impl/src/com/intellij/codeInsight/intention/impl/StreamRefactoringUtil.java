// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.util.OptionalRefactoringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class StreamRefactoringUtil {
  static boolean isRefactoringCandidate(PsiExpression expression, boolean requireExpressionLambda) {
    if(expression instanceof PsiLambdaExpression lambdaExpression) {
      return lambdaExpression.getParameterList().getParametersCount() == 1 &&
             (!requireExpressionLambda || LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody()) != null);
    } else if(expression instanceof PsiMethodReferenceExpression methodRef) {
      return methodRef.resolve() != null && LambdaRefactoringUtil.canConvertToLambdaWithoutSideEffects(methodRef);
    }
    return false;
  }

  @NotNull
  public static String generateMapOperation(PsiVariable variable, @Nullable PsiType outType, PsiElement mapper) {
    String shortcutMappingMethod = getShortcutMappingMethod(variable, outType, mapper);
    if (shortcutMappingMethod != null) return shortcutMappingMethod.isEmpty() ? "" : "." + shortcutMappingMethod + "()";
    PsiType inType = variable.getType();
    String operationName = getMapOperationName(inType, outType);
    if(outType != null && mapper instanceof PsiArrayInitializerExpression) {
      mapper = CommonJavaRefactoringUtil.convertInitializerToNormalExpression((PsiExpression)mapper, outType);
    }
    String typeArgument = mapper instanceof PsiExpression ? OptionalRefactoringUtil.getMapTypeArgument((PsiExpression)mapper, outType) : "";
    return "." + typeArgument + operationName +
           "(" + variable.getName() + "->" + mapper.getText() + ")";
  }

  /**
   * Returns the shortcut mapping method name
   *
   * @param variable mapper input variable
   * @param outType  output type of the mapper
   * @param mapper   mapper code
   * @return shortcut mapping name ("boxed", "asLongStream", "asDoubleStream") if applicable, empty string if it's
   * ditto mapping (no mapping is necessary at all) and null if no shortcut is applicable for given mapper
   */
  @Nullable
  public static String getShortcutMappingMethod(PsiVariable variable, @Nullable PsiType outType, PsiElement mapper) {
    if (!(mapper instanceof PsiExpression)) return null;
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(((PsiExpression)mapper));
    PsiType inType = variable.getType();
    if (expression instanceof PsiTypeCastExpression && inType instanceof PsiPrimitiveType && Objects.equals(expression.getType(), outType)) {
      expression = ((PsiTypeCastExpression)expression).getOperand();
    }
    if (ExpressionUtils.isReferenceTo(expression, variable)) {
      if (!(outType instanceof PsiPrimitiveType)) {
        return inType instanceof PsiPrimitiveType ? "boxed" : "";
      }
      if (outType.equals(inType)) {
        return "";
      }
      if (PsiTypes.longType().equals(outType) && PsiTypes.intType().equals(inType)) {
        return "asLongStream";
      }
      if (PsiTypes.doubleType().equals(outType) && (PsiTypes.longType().equals(inType) || PsiTypes.intType().equals(inType))) {
        return "asDoubleStream";
      }
    }
    return null;
  }

  /**
   * Returns name of the Stream API mapping operation which maps from inType to outType (assuming both types are supported by Stream API)
   *
   * @param inType input stream element type
   * @param outType output stream element type
   * @return a name of the mapping operation like "map" or "mapToLong".
   */
  @NotNull
  public static String getMapOperationName(PsiType inType, @Nullable PsiType outType) {
    if(outType instanceof PsiPrimitiveType) {
      if(!outType.equals(inType)) {
        if(PsiTypes.intType().equals(outType)) {
          return "mapToInt";
        } else if(PsiTypes.longType().equals(outType)) {
          return "mapToLong";
        } else if(PsiTypes.doubleType().equals(outType)) {
          return "mapToDouble";
        }
      }
    } else if(inType instanceof PsiPrimitiveType) {
      return "mapToObj";
    }
    return "map";
  }

  @Nullable
  public static String getFlatMapOperationName(PsiType inType, PsiType outType) {
    if (!(inType instanceof PsiPrimitiveType)) {
      if (PsiTypes.intType().equals(outType)) {
        return "flatMapToInt";
      }
      else if (PsiTypes.longType().equals(outType)) {
        return "flatMapToLong";
      }
      else if (PsiTypes.doubleType().equals(outType)) {
        return "flatMapToDouble";
      }
    } else if (!inType.equals(outType)) return null;
    return "flatMap";
  }
}
