/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class OptionalUtil {
  private static final String OPTIONAL_INT = "java.util.OptionalInt";
  private static final String OPTIONAL_LONG = "java.util.OptionalLong";
  private static final String OPTIONAL_DOUBLE = "java.util.OptionalDouble";

  @NotNull
  @Contract(pure = true)
  public static String getOptionalClass(String type) {
    switch (type) {
      case "int":
        return OPTIONAL_INT;
      case "long":
        return OPTIONAL_LONG;
      case "double":
        return OPTIONAL_DOUBLE;
      default:
        return CommonClassNames.JAVA_UTIL_OPTIONAL;
    }
  }

  public static boolean isOptionalClassName(String className) {
    return CommonClassNames.JAVA_UTIL_OPTIONAL.equals(className) ||
         OPTIONAL_INT.equals(className) || OPTIONAL_LONG.equals(className) || OPTIONAL_DOUBLE.equals(className);
  }

  /**
   * Unwraps an {@link java.util.Optional}, {@link java.util.OptionalInt}, {@link java.util.OptionalLong} or {@link java.util.OptionalDouble}
   * returning its element type
   *
   * @param type a type representing optional (e.g. {@code Optional<String>} or {@code OptionalInt})
   * @return an element type (e.g. {@code String} or {@code int}). Returns {@code null} if the supplied type is not an optional type
   * or its a raw {@code java.util.Optional}.
   */
  @Contract("null -> null")
  public static PsiType getOptionalElementType(PsiType type) {
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if(aClass == null) return null;
    String className = aClass.getQualifiedName();
    if(className == null) return null;
    switch (className) {
      case "java.util.OptionalInt":
        return PsiType.INT;
      case "java.util.OptionalLong":
        return PsiType.LONG;
      case "java.util.OptionalDouble":
        return PsiType.DOUBLE;
      case CommonClassNames.JAVA_UTIL_OPTIONAL:
        PsiType[] parameters = ((PsiClassType)type).getParameters();
        if (parameters.length != 1) return null;
        PsiType streamType = parameters[0];
        if (streamType instanceof PsiCapturedWildcardType) {
          streamType = ((PsiCapturedWildcardType)streamType).getUpperBound();
        }
        return streamType;
      default:
        return null;
    }
  }

  /**
   * Generates an expression text which will unwrap an {@link java.util.Optional}.
   *
   * @param qualifier the text representing a qualifier of Optional type
   * @param var a variable used to refer optional value inside {@code trueExpression}
   * @param trueExpression an expression which should be evaluated if Optional is non-empty
   * @param falseExpression an expression which should be returned if Optional is empty
   * @param targetType a type of target expression
   * @param useOrElseGet if true, use orElseGet if necessary
   * @return an expression text which will unwrap an {@code Optional}.
   */
  public static String generateOptionalUnwrap(String qualifier, PsiVariable var,
                                               PsiExpression trueExpression, PsiExpression falseExpression,
                                               PsiType targetType, boolean useOrElseGet) {
    if (!ExpressionUtils.isReferenceTo(trueExpression, var)) {
      if(trueExpression instanceof PsiTypeCastExpression && ExpressionUtils.isNullLiteral(falseExpression)) {
        PsiTypeCastExpression castExpression = (PsiTypeCastExpression)trueExpression;
        PsiTypeElement castType = castExpression.getCastType();
        // pull cast outside to avoid the .map() step
        if(castType != null && ExpressionUtils.isReferenceTo(castExpression.getOperand(), var)) {
          return "(" + castType.getText() + ")" + qualifier + ".orElse(null)";
        }
      }
      if(ExpressionUtils.isLiteral(falseExpression, Boolean.FALSE) && PsiType.BOOLEAN.equals(trueExpression.getType())) {
        return qualifier + ".filter(" + LambdaUtil.createLambda(var, trueExpression) + ").isPresent()";
      }
      if(trueExpression instanceof PsiConditionalExpression) {
        PsiConditionalExpression condition = (PsiConditionalExpression)trueExpression;
        PsiExpression elseExpression = condition.getElseExpression();
        if(elseExpression != null && PsiEquivalenceUtil.areElementsEquivalent(falseExpression, elseExpression)) {
          return generateOptionalUnwrap(
            qualifier + ".filter(" + LambdaUtil.createLambda(var, condition.getCondition()) + ")", var,
            condition.getThenExpression(), falseExpression, targetType, useOrElseGet);
        }
      }
      if(isOptionalEmptyCall(falseExpression)) {
        // simplify "qualifier.map(x -> Optional.of(x)).orElse(Optional.empty())" to "qualifier"
        if (trueExpression instanceof PsiMethodCallExpression &&
            MethodCallUtils.isCallToStaticMethod((PsiMethodCallExpression)trueExpression, CommonClassNames.JAVA_UTIL_OPTIONAL, "of", 1)) {
          PsiExpression arg = ((PsiMethodCallExpression)trueExpression).getArgumentList().getExpressions()[0];
          if(ExpressionUtils.isReferenceTo(arg, var)) {
            return qualifier;
          }
          return qualifier + ".map(" + LambdaUtil.createLambda(var, arg) + ")";
        }
        return qualifier + ".flatMap(" + LambdaUtil.createLambda(var, trueExpression) + ")";
      }
      trueExpression =
        targetType == null ? trueExpression : RefactoringUtil.convertInitializerToNormalExpression(trueExpression, targetType);
      String typeArg = getMapTypeArgument(trueExpression, targetType);
      qualifier += "." + typeArg + "map(" + LambdaUtil.createLambda(var, trueExpression) + ")";
    }
    if (useOrElseGet && !ExpressionUtils.isSimpleExpression(falseExpression)) {
      return qualifier + ".orElseGet(() -> " + falseExpression.getText() + ")";
    } else {
      return qualifier + ".orElse(" + falseExpression.getText() + ")";
    }
  }

  @Contract("null -> false")
  public static boolean isOptionalEmptyCall(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression &&
           MethodCallUtils.isCallToStaticMethod((PsiMethodCallExpression)expression, CommonClassNames.JAVA_UTIL_OPTIONAL, "empty", 0);
  }

  @NotNull
  public static String getMapTypeArgument(PsiExpression expression, PsiType type) {
    if (!(type instanceof PsiClassType)) return "";
    PsiExpression copy =
      JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText(), expression);
    PsiType exprType = copy.getType();
    if (exprType != null &&
        !exprType.equals(PsiType.NULL) &&
        !LambdaUtil.notInferredType(exprType) &&
        TypeConversionUtil.isAssignable(type, exprType)) {
      return "";
    }
    return "<" + type.getCanonicalText() + ">";
  }
}
