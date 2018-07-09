// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class OptionalUtil {
  private static final String OPTIONAL_INT = "java.util.OptionalInt";
  private static final String OPTIONAL_LONG = "java.util.OptionalLong";
  private static final String OPTIONAL_DOUBLE = "java.util.OptionalDouble";

  private static final CallMatcher OPTIONAL_OF =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "of", "ofNullable").parameterCount(1);

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
                                              @Nullable PsiType targetType, boolean useOrElseGet) {
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(trueExpression);
    PsiType trueType = trueExpression.getType();
    boolean trivialMap = ExpressionUtils.isReferenceTo(trueExpression, var) &&
                         targetType != null &&
                         (trueType instanceof PsiLambdaParameterType || Objects.requireNonNull(trueType).isAssignableFrom(targetType));
    if (!trivialMap) {
      if (stripped instanceof PsiTypeCastExpression && ExpressionUtils.isNullLiteral(falseExpression)) {
        PsiTypeCastExpression castExpression = (PsiTypeCastExpression)stripped;
        PsiTypeElement castType = castExpression.getCastType();
        // pull cast outside to avoid the .map() step
        if (castType != null && ExpressionUtils.isReferenceTo(castExpression.getOperand(), var)) {
          return "(" + castType.getText() + ")" + qualifier + ".orElse(null)";
        }
      }
      if (ExpressionUtils.isLiteral(falseExpression, Boolean.FALSE) && PsiType.BOOLEAN.equals(trueType)) {
        if (ExpressionUtils.isLiteral(trueExpression, Boolean.TRUE)) {
          return qualifier + ".isPresent()";
        }
        return qualifier + ".filter(" + LambdaUtil.createLambda(var, trueExpression) + ").isPresent()";
      }
      if (ExpressionUtils.isLiteral(falseExpression, Boolean.TRUE) && ExpressionUtils.isLiteral(trueExpression, Boolean.FALSE)) {
        return "!" + qualifier + ".isPresent()";
      }
      if (stripped instanceof PsiConditionalExpression) {
        PsiConditionalExpression condition = (PsiConditionalExpression)stripped;
        PsiExpression thenExpression = condition.getThenExpression();
        PsiExpression elseExpression = condition.getElseExpression();
        if (elseExpression != null && PsiEquivalenceUtil.areElementsEquivalent(falseExpression, elseExpression)) {
          return generateOptionalUnwrap(
            qualifier + ".filter(" + LambdaUtil.createLambda(var, condition.getCondition()) + ")", var,
            condition.getThenExpression(), falseExpression, targetType, useOrElseGet);
        }
        if (thenExpression != null && PsiEquivalenceUtil.areElementsEquivalent(falseExpression, thenExpression)) {
          return generateOptionalUnwrap(
            qualifier + ".filter(" + var.getName() + " -> " + BoolUtils.getNegatedExpressionText(condition.getCondition()) + ")", var,
            condition.getElseExpression(), falseExpression, targetType, useOrElseGet);
        }
      }
      String suffix = null;
      boolean java9 = PsiUtil.isLanguageLevel9OrHigher(trueExpression);
      if (isOptionalEmptyCall(falseExpression)) {
        suffix = "";
      }
      else if (java9 && InheritanceUtil.isInheritor(falseExpression.getType(), CommonClassNames.JAVA_UTIL_OPTIONAL) &&
               LambdaGenerationUtil.canBeUncheckedLambda(falseExpression)) {
        suffix = ".or(() -> " + falseExpression.getText() + ")";
      }
      if (suffix != null) {
        PsiMethodCallExpression mappedOptional = ObjectUtils.tryCast(stripped, PsiMethodCallExpression.class);
        // simplify "qualifier.map(x -> Optional.of(x)).orElse(Optional.empty())" to "qualifier"
        if (OPTIONAL_OF.test(mappedOptional)) {
          PsiExpression arg = mappedOptional.getArgumentList().getExpressions()[0];
          if (ExpressionUtils.isReferenceTo(arg, var)) {
            return qualifier + suffix;
          }
          return qualifier + ".map(" + LambdaUtil.createLambda(var, arg) + ")" + suffix;
        }
        if (suffix.isEmpty()) {
          return qualifier + ".flatMap(" + LambdaUtil.createLambda(var, trueExpression) + ")";
        }
      }
      if (java9 && StreamApiUtil.isNullOrEmptyStream(falseExpression) && !ExpressionUtils.isNullLiteral(falseExpression)) {
        PsiType elementType = StreamApiUtil.getStreamElementType(targetType);
        if (elementType != null) {
          PsiMethodCallExpression mappedStream = ObjectUtils.tryCast(stripped, PsiMethodCallExpression.class);
          if (mappedStream != null && "of".equals(mappedStream.getMethodExpression().getReferenceName())) {
            PsiExpression[] args = mappedStream.getArgumentList().getExpressions();
            if (args.length == 1) {
              PsiMethod method = mappedStream.resolveMethod();
              if(method != null && method.getContainingClass() != null) {
                String className = method.getContainingClass().getQualifiedName();
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if(className != null && className.startsWith("java.util.stream.")
                   && parameters.length == 1
                   && !(parameters[0].getType() instanceof PsiArrayType)) {
                  PsiExpression arg = args[0];
                  if(ExpressionUtils.isReferenceTo(arg, var)) {
                    return qualifier + ".stream()";
                  }
                  if(arg.getType() != null && elementType.isAssignableFrom(arg.getType())) {
                    return qualifier + ".stream()" + StreamRefactoringUtil.generateMapOperation(var, elementType, arg);
                  }
                }
              }
            }
          }
          String flatMapOperationName = StreamRefactoringUtil.getFlatMapOperationName(var.getType(), elementType);
          if(flatMapOperationName != null) {
            return qualifier + ".stream()."+flatMapOperationName+"(" + LambdaUtil.createLambda(var, trueExpression) + ")";
          }
        }
      }
      trueExpression =
        targetType == null ? trueExpression : RefactoringUtil.convertInitializerToNormalExpression(trueExpression, targetType);
      String typeArg = getMapTypeArgument(trueExpression, targetType, falseExpression);
      qualifier += "." + typeArg + "map(" + LambdaUtil.createLambda(var, trueExpression) + ")";
    }
    if (useOrElseGet && !ExpressionUtils.isSafelyRecomputableExpression(falseExpression)) {
      return qualifier + ".orElseGet(() -> " + falseExpression.getText() + ")";
    } else {
      PsiType falseType = falseExpression.getType();
      String falseText = falseExpression.getText();
      if (falseType instanceof PsiPrimitiveType && targetType instanceof PsiPrimitiveType && !(targetType.equals(falseType))) {
        Number falseValue = JavaPsiMathUtil.getNumberFromLiteral(falseExpression);
        if (falseValue != null) {
          falseText = falseValue.toString();
          if (targetType.equals(PsiType.FLOAT)) {
            falseText += "F";
          } else if(targetType.equals(PsiType.DOUBLE) && !falseText.contains(".")) {
            falseText += ".0";
          } else if(targetType.equals(PsiType.LONG)) {
            falseText += "L";
          }
        }
      }
      return qualifier + ".orElse(" + falseText + ")";
    }
  }

  @Contract("null -> false")
  public static boolean isOptionalEmptyCall(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression &&
           MethodCallUtils.isCallToStaticMethod((PsiMethodCallExpression)expression, CommonClassNames.JAVA_UTIL_OPTIONAL, "empty", 0);
  }

  @NotNull
  public static String getMapTypeArgument(PsiExpression expression, PsiType type) {
    return getMapTypeArgument(expression, type, null);
  }

  @NotNull
  private static String getMapTypeArgument(PsiExpression expression, PsiType type, PsiExpression falseExpression) {
    if (!(type instanceof PsiClassType)) return "";
    PsiExpression copy =
      JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText(), expression);
    PsiType exprType = copy.getType();
    if (exprType != null &&
        !exprType.equals(PsiType.NULL) &&
        !LambdaUtil.notInferredType(exprType) &&
        TypeConversionUtil.isAssignable(type, exprType)) {
      if (falseExpression == null) return "";
      PsiType falseType = falseExpression.getType();
      if (falseType != null && (falseType.isAssignableFrom(exprType) || falseType.equals(PsiType.NULL))) return "";
    }
    return "<" + type.getCanonicalText() + ">";
  }
}
