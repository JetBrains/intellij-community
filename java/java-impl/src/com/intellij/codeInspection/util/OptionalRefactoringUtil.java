// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class OptionalRefactoringUtil {
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
                                              @NotNull PsiExpression trueExpression, PsiExpression falseExpression,
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
        if (thenExpression != null && elseExpression != null) {
          if (PsiEquivalenceUtil.areElementsEquivalent(falseExpression, elseExpression)) {
            return generateOptionalUnwrap(
              qualifier + ".filter(" + LambdaUtil.createLambda(var, condition.getCondition()) + ")", var,
              thenExpression, falseExpression, targetType, useOrElseGet);
          }
          if (PsiEquivalenceUtil.areElementsEquivalent(falseExpression, thenExpression)) {
            return generateOptionalUnwrap(
              qualifier + ".filter(" + var.getName() + " -> " + BoolUtils.getNegatedExpressionText(condition.getCondition()) + ")", var,
              elseExpression, falseExpression, targetType, useOrElseGet);
          }
        }
      }
      String suffix = null;
      boolean java9 = PsiUtil.isLanguageLevel9OrHigher(trueExpression);
      if (OptionalUtil.isOptionalEmptyCall(falseExpression)) {
        suffix = "";
      }
      else if (java9 && InheritanceUtil.isInheritor(falseExpression.getType(), CommonClassNames.JAVA_UTIL_OPTIONAL) &&
               LambdaGenerationUtil.canBeUncheckedLambda(falseExpression)) {
        suffix = ".or(() -> " + falseExpression.getText() + ")";
      }
      if (suffix != null) {
        PsiMethodCallExpression mappedOptional = ObjectUtils.tryCast(stripped, PsiMethodCallExpression.class);
        // simplify "qualifier.map(x -> Optional.of(x)).orElse(Optional.empty())" to "qualifier"
        if (OptionalUtil.JDK_OPTIONAL_WRAP_METHOD.test(mappedOptional)) {
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
          if(flatMapOperationName != null && !SideEffectChecker.mayHaveSideEffects(trueExpression)) {
            return qualifier + ".stream()."+flatMapOperationName+"(" + LambdaUtil.createLambda(var, trueExpression) + ")";
          }
        }
      }
      trueExpression =
        targetType == null ? trueExpression : CommonJavaRefactoringUtil.convertInitializerToNormalExpression(trueExpression, targetType);
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

  @NotNull
  public static String getMapTypeArgument(PsiExpression expression, PsiType type) {
    return getMapTypeArgument(expression, type, null);
  }

  @NotNull
  private static String getMapTypeArgument(PsiExpression expression, PsiType type, PsiExpression falseExpression) {
    if (!(type instanceof PsiClassType)) return "";
    String text = expression.getText();
    // PsiEmptyExpressionImpl might be passed here
    if (text.isEmpty()) return "";
    PsiExpression copy = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(text, expression);
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
