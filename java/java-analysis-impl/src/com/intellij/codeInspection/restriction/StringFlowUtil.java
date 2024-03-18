// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.restriction;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;

import java.util.List;

public final class StringFlowUtil {

  /**
   * Extracts return value without qualifier from getter-like method.
   */
  @Nullable
  public static UExpression getReturnValue(@NotNull UCallExpression call) {
    PsiMethod psiMethod = call.resolve();
    UExpression returnValue = UastContextKt.toUElement(PropertyUtilBase.getGetterReturnExpression(psiMethod), UExpression.class);
    if (returnValue instanceof UQualifiedReferenceExpression) {
      returnValue = ((UQualifiedReferenceExpression)returnValue).getSelector();
    }
    return returnValue;
  }

  /**
   * Traverses tree up starting from expression while current expression result may depend on starting expression result.
   * 
   * @param expression starting expression
   * @param allowStringTransformation true if transformations like concatenation are allowed
   * @param factory restriction info factory. used to check if we can go up through some method calls
   *               (e.g. if this method return value is marked with some restriction or not)  
   * @return last expression that we can go through
   */
  public static @NotNull UExpression goUp(@NotNull UExpression expression,
                                          boolean allowStringTransformation,
                                          @NotNull RestrictionInfoFactory<?> factory) {
    UExpression parent = expression;
    while (true) {
      UElement parentElement = parent.getUastParent();
      if (parentElement instanceof ULocalVariable && parentElement.getUastParent() instanceof UDeclarationsExpression) {
        // Kotlin has strange hierarchy for elvis operator
        UExpressionList elvis = ObjectUtils.tryCast(parentElement.getUastParent().getUastParent(), UExpressionList.class);
        if (elvis != null) {
          parentElement = elvis;
        }
      }
      if (parentElement instanceof UExpressionList) {
        UExpression lastExpression = ContainerUtil.getLastItem(((UExpressionList)parentElement).getExpressions());
        if (lastExpression != null && AnnotationContext.expressionsAreEquivalent(parent, lastExpression)) {
          // Result of expression list is the last expression in the list in Kotlin
          parentElement = parentElement.getUastParent();
        }
      }
      UExpression next = ObjectUtils.tryCast(parentElement, UExpression.class);
      if (next == null || next instanceof UNamedExpression) return parent;
      if (next instanceof USwitchClauseExpression switchClause) {
        List<UExpression> caseValues = ContainerUtil.map(switchClause.getCaseValues(), caseValue -> AnnotationContext.normalize(caseValue));
        if (caseValues.contains(AnnotationContext.normalize(parent))) return parent;
        UExpressionList switchBody = ObjectUtils.tryCast(next.getUastParent(), UExpressionList.class);
        if (switchBody == null) return parent;
        USwitchExpression switchExpression = ObjectUtils.tryCast(switchBody.getUastParent(), USwitchExpression.class);
        if (switchExpression == null) return parent;
        next = switchExpression;
      }
      ULambdaExpression lambda = ObjectUtils.tryCast(next, ULambdaExpression.class);
      if (next instanceof UReturnExpression) {
        lambda = ObjectUtils.tryCast(((UReturnExpression)next).getJumpTarget(), ULambdaExpression.class);
        if (lambda == null) return parent;
      }
      if (lambda != null) {
        UCallExpression uastParent = ObjectUtils.tryCast(lambda.getUastParent(), UCallExpression.class);
        if (uastParent == null) return parent;
        PsiMethod method = uastParent.resolve();
        if (method == null || !isPassthroughMethod(method, uastParent, lambda, factory)) return parent;
        next = uastParent;
      }
      if (next instanceof UQualifiedReferenceExpression && !TypeUtils.isJavaLangString(next.getExpressionType())
          && !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE, next.getExpressionType())) {
        return parent;
      }
      if (next instanceof UPolyadicExpression &&
          (!allowStringTransformation || ((UPolyadicExpression)next).getOperator() != UastBinaryOperator.PLUS)) {
        return parent;
      }
      if (next instanceof UCallExpression) {
        if (!UastExpressionUtils.isArrayInitializer(next) && !UastExpressionUtils.isNewArrayWithInitializer(next)) {
          PsiMethod method = ((UCallExpression)next).resolve();
          boolean shouldGoThroughCall =
            TypeUtils.isJavaLangString(next.getExpressionType()) &&
            (allowStringTransformation && isStringProcessingMethod(method, factory) ||
             isPassthroughMethod(method, (UCallExpression)next, parent, factory));
          if (!shouldGoThroughCall) return parent;
        }
      }
      if (next instanceof UIfExpression && AnnotationContext.expressionsAreEquivalent(parent, ((UIfExpression)next).getCondition())) {
        return parent;
      }
      if (next instanceof USwitchExpression) {
        final UExpression condExpression = ((USwitchExpression)next).getExpression();
        if (condExpression != null && AnnotationContext.expressionsAreEquivalent(parent, condExpression)) {
          return parent;
        }
      }
      if (next instanceof UArrayAccessExpression) {
        return parent;
      }
      parent = next;
    }
  }

  /**
   * Checks if the method is detected to be a string-processing method. A string processing method is a method that:
   * <ul>
   *   <li>Pure (either explicitly marked or inferred)</li>
   *   <li>Accepts parameters</li>
   *   <li>No parameters are marked with restriction info</li>
   *   <li>Return value is not marked with restriction info</li>
   * </ul>
   *
   * @param method method to check
   * @return true if method is detected to be a string-processing method
   */
  public static boolean isStringProcessingMethod(@Nullable PsiMethod method,
                                                 @NotNull RestrictionInfoFactory<?> factory) {
    if (method == null) return false;
    if (factory.fromModifierListOwner(method).getKind() == RestrictionInfo.RestrictionInfoKind.KNOWN) return false;
    if (!JavaMethodContractUtil.isPure(method)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) return false;
    for (PsiParameter parameter : parameters) {
      if (factory.fromModifierListOwner(parameter).getKind() == RestrictionInfo.RestrictionInfoKind.KNOWN) return false;
    }
    return true;
  }

  /**
   * Checks if method is passthrough.
   * <p>
   * Method is passthrough if:
   * <ul>
   *   <li>it doesn't have a type parameter with extends list as return type and it is kotlin passthrough method {@see StringFlowUtil.isKotlinPassthroughMethod}</li>
   *   <li>it has parameter bounded to arg and this parameter type can be resolved to non-parameterized type</li>
   *   <li>there's no method call or argument and one of method parameters can be resolved to non-parameterized type</li>
   * </ul>
   *
   * @param method  method to check
   * @param call    this method call
   * @param arg     argument which is passed
   * @param factory restriction factory to check if there's any restrictions related to method
   */
  public static boolean isPassthroughMethod(@Nullable PsiMethod method,
                                            @Nullable UCallExpression call,
                                            @Nullable UExpression arg,
                                            @NotNull RestrictionInfoFactory<?> factory) {
    if (method == null) return false;
    PsiType type = method.getReturnType();
    PsiTypeParameter typeParameter = ObjectUtils.tryCast(PsiUtil.resolveClassInClassTypeOnly(type), PsiTypeParameter.class);
    if (typeParameter != null && typeParameter.getExtendsList().getReferencedTypes().length == 0) {
      PsiParameter[] parameters;
      if (arg == null || call == null) {
        parameters = method.getParameterList().getParameters();
      }
      else {
        PsiParameter parameter = AnnotationContext.getParameter(call, arg);
        if (parameter == null) return false;
        PsiType parameterType = parameter.getType();
        PsiElement psi = call.getSourcePsi();
        if (psi instanceof PsiMethodCallExpression) {
          PsiSubstitutor substitutor = ((PsiMethodCallExpression)psi).getMethodExpression().advancedResolve(false).getSubstitutor();
          parameterType = substitutor.substitute(parameterType);
        }
        RestrictionInfo info = factory.fromAnnotationOwner(parameterType);
        if (info.getKind() != RestrictionInfo.RestrictionInfoKind.UNKNOWN) {
          return false;
        }
        parameters = new PsiParameter[]{parameter};
      }
      for (PsiParameter parameter : parameters) {
        PsiType parameterType = parameter.getType();
        if (type.equals(GenericsUtil.getVariableTypeByExpressionType(parameterType))) return true;
        PsiType returnType = GenericsUtil.getVariableTypeByExpressionType(LambdaUtil.getFunctionalInterfaceReturnType(parameterType));
        if (type.equals(returnType)) return true;
      }
    }
    return isKotlinPassthroughMethod(method);
  }

  /**
   * Checks if method is kotlin passthrough.
   * <p>
   * Method is kotlin passthrough if:
   * <ul>
   *   <li>it is <code>let</code> or <code>run</code> inline call</li>
   *   <li>it is <code>joinToString</code> method call</li>
   *   <li>it is any <code>static</code> method without parameters from <code>StringsKt__Strings</code></li>
   * </ul>
   */
  private static boolean isKotlinPassthroughMethod(PsiMethod method) {
    if ((method.getName().equals("let") || method.getName().equals("run")) &&
        method.getModifierList().textMatches("public inline")) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 2 && isReceiver(method, parameters[0]) && parameters[1].getName().equals("block")) {
        return true;
      }
    }
    if (method.getName().equals("joinToString")) {
      PsiClass aClass = method.getContainingClass();
      return aClass != null && "kotlin.collections.CollectionsKt___CollectionsKt".equals(aClass.getQualifiedName());
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      PsiParameter parameter = method.getParameterList().getParameter(0);
      if (parameter != null && isReceiver(method, parameter)) {
        PsiClass aClass = method.getContainingClass();
        return aClass != null &&
               aClass.getQualifiedName() != null &&
               aClass.getQualifiedName().startsWith("kotlin.text.StringsKt__Strings");
      }
    }
    return false;
  }

  private static boolean isReceiver(PsiMethod method, PsiParameter param) {
    return param.getName().equals("$receiver") || param.getName().equals("$this$" + method.getName());
  }
}
