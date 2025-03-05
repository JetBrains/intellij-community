// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE;


/**
 * @author Bas Leijdekkers
 * @author Tagir Valeev
 */
abstract class BaseEqualsVisitor extends BaseInspectionVisitor {

  static final CallMatcher OBJECT_EQUALS =
    CallMatcher.instanceCall(JAVA_LANG_OBJECT, "equals").parameterTypes(JAVA_LANG_OBJECT);
  private static final CallMatcher STATIC_EQUALS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.util.Objects", "equals").parameterCount(2),
      CallMatcher.staticCall("com.google.common.base.Objects", "equal").parameterCount(2));
  private static final CallMatcher PREDICATE_TEST =
    CallMatcher.instanceCall(JAVA_UTIL_FUNCTION_PREDICATE, "test").parameterCount(1);
  private static final CallMatcher PREDICATE_IS_EQUAL =
    CallMatcher.staticCall(JAVA_UTIL_FUNCTION_PREDICATE, "isEqual").parameterCount(1);
  private static final CallMatcher PREDICATE_NOT = CallMatcher.staticCall(JAVA_UTIL_FUNCTION_PREDICATE, "not").parameterCount(1);
  private static final CallMatcher PREDICATE_OR = CallMatcher.instanceCall(JAVA_UTIL_FUNCTION_PREDICATE, "or").parameterCount(1);
  private static final CallMatcher PREDICATE_AND = CallMatcher.instanceCall(JAVA_UTIL_FUNCTION_PREDICATE, "and").parameterCount(1);
  private static final CallMatcher PREDICATE_NEGATE = CallMatcher.instanceCall(JAVA_UTIL_FUNCTION_PREDICATE, "negate").parameterCount(0);
  private static final CallMatcher PREDICATE_NOT_OR_AND = CallMatcher.anyOf(PREDICATE_NOT,
                                                                            PREDICATE_OR,
                                                                            PREDICATE_AND);
  private static final CallMatcher PREDICATE_OR_AND_NEGATE = CallMatcher.anyOf(PREDICATE_OR,
                                                                               PREDICATE_AND,
                                                                               PREDICATE_NEGATE);

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    super.visitMethodReferenceExpression(expression);
    if (!OBJECT_EQUALS.methodReferenceMatches(expression) && !STATIC_EQUALS.methodReferenceMatches(expression)) {
      return;
    }
    final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (method == null) {
      return;
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(method, resolveResult);
    final PsiType leftType, rightType;
    if (parameters.length == 2) {
      leftType = substitutor.substitute(parameters[0].getType());
      rightType = substitutor.substitute(parameters[1].getType());
    }
    else {
      final PsiExpression qualifier = expression.getQualifierExpression();
      assert qualifier != null;
      leftType = qualifier.getType();
      rightType = substitutor.substitute(parameters[0].getType());
    }
    if (leftType != null && rightType != null) checkTypes(expression, leftType, rightType);
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    if (OBJECT_EQUALS.test(expression)) {
      PsiExpression expression1 =
        PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(expression.getMethodExpression()));
      PsiExpression expression2 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      checkTypes(expression, expression1, expression2);
    }
    else if (STATIC_EQUALS.test(expression)) {
      PsiExpression expression1 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      PsiExpression expression2 = PsiUtil.skipParenthesizedExprDown(arguments[1]);
      checkTypes(expression, expression1, expression2);
    }
    else if (PREDICATE_IS_EQUAL.test(expression)) {
      PsiExpression expression1 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      PsiType psiType1 = getType(expression1);
      PsiType psiType2 = resolveIsEqualPredicateType(expression);
      if (psiType1 == null || psiType2 == null) {
        return;
      }
      checkTypes(expression.getMethodExpression(), psiType1, psiType2);
    }
  }

  private static @Nullable PsiType resolveIsEqualPredicateType(@NotNull PsiMethodCallExpression expression) {
    PsiExpression highestPredicate = expression;
    int max = 100;
    int currentLevel = 0;
    while (currentLevel <= max) {
      currentLevel++;

      //example: Predicate.isEqual("1").or(...) or Predicate.isEqual("1").and(...) or Predicate.isEqual("1").negate()
      final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(highestPredicate);
      if (PREDICATE_OR_AND_NEGATE.matches(call)) {
        highestPredicate = call;
        continue;
      }

      //example: Predicate.not(Predicate.isEqual("1")) or predicate.or(Predicate.isEqual("1")) or predicate.and(Predicate.isEqual("1"))
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(highestPredicate.getParent());
      if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiExpression psiExpressionNextParent) {
        if (PREDICATE_NOT_OR_AND.matches(psiExpressionNextParent)) {
          highestPredicate = psiExpressionNextParent;
          continue;
        }
      }

      break;
    }

    return findPsiTypeForPredicate(highestPredicate);
  }

  private static @Nullable PsiType findPsiTypeForPredicate(@NotNull PsiExpression predicate) {
    PsiType typeParameter =
      PsiUtil.substituteTypeParameter(predicate.getType(), JAVA_UTIL_FUNCTION_PREDICATE, 0, false);
    if (typeParameter != null && !typeParameter.equalsToText(JAVA_LANG_OBJECT)) {
      return typeParameter;
    }

    final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(predicate);
    if (PREDICATE_TEST.test(call)) {
      final PsiExpression[] argumentExpressions = call.getArgumentList().getExpressions();
      if (argumentExpressions.length != 1) {
        return null;
      }
      PsiExpression argument = PsiUtil.skipParenthesizedExprDown(argumentExpressions[0]);
      return getType(argument);
    }

    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(predicate.getParent());
    if (parent instanceof PsiExpressionList) {
      return findPredicateExpectedType(predicate);
    }
    return null;
  }

  private static @Nullable PsiType findPredicateExpectedType(@NotNull PsiExpression predicate) {
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(predicate, true);
    if (!(expectedType instanceof PsiClassType classType)) {
      return null;
    }
    if (classType.getParameterCount() != 1 || !PsiTypesUtil.classNameEquals(classType, JAVA_UTIL_FUNCTION_PREDICATE)) {
      return null;
    }
    PsiType parameter = classType.getParameters()[0];
    //Object can be cast to any
    if (parameter == null || parameter.equalsToText(JAVA_LANG_OBJECT)) {
      return null;
    }
    if (parameter instanceof PsiWildcardType psiWildcardType) {
      parameter = psiWildcardType.isSuper() ? psiWildcardType.getSuperBound() : parameter;
    }
    return parameter;
  }

  private void checkTypes(@NotNull PsiMethodCallExpression expression, PsiExpression expression1, PsiExpression expression2) {
    if (expression1 == null || expression2 == null) {
      return;
    }
    final PsiType leftType = getType(expression1);
    final PsiType rightType = getType(expression2);
    if (leftType != null && rightType != null) {
      if (!checkTypes(expression.getMethodExpression(), leftType, rightType)) {
        CommonDataflow.DataflowResult dfa = CommonDataflow.getDataflowResult(expression);
        if (dfa != null) {
          Project project = getCurrentFile().getProject();
          PsiType refinedLeftType =
            leftType instanceof PsiPrimitiveType ? leftType : TypeConstraint.fromDfType(dfa.getDfType(expression1)).getPsiType(project);
          PsiType refinedRightType =
            rightType instanceof PsiPrimitiveType ? rightType : TypeConstraint.fromDfType(dfa.getDfType(expression2)).getPsiType(project);
          if (refinedLeftType != null && refinedRightType != null &&
              (!refinedLeftType.equals(leftType) || !refinedRightType.equals(rightType))) {
            checkTypes(expression.getMethodExpression(), refinedLeftType, refinedRightType);
          }
        }
      }
    }
  }

  protected static PsiType getType(PsiExpression expression) {
    if (!(expression instanceof PsiNewExpression newExpression)) {
      return expression.getType();
    }
    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    return anonymousClass != null ? anonymousClass.getBaseClassType() : expression.getType();
  }

  /**
   * @param expression anchor to report error
   * @param leftType   left type
   * @param rightType  right type
   * @return true if error is reported due to incompatible types
   */
  abstract boolean checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType);
}
