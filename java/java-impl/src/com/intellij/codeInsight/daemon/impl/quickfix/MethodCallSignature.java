// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLambdaExpressionType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.search.ApproximateResolver;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches the signature of a method against a call, without type inference. The static import fix uses this check
 * to keep the editor hint away from a method the call cannot use.
 */
final class MethodCallSignature {
  private MethodCallSignature() {
  }

  /**
   * @return the substitutor which the argument list infers for the method
   */
  static @NotNull PsiSubstitutor inferSubstitutor(@NotNull PsiMethod method, @NotNull PsiExpressionList argumentList) {
    MethodCandidateInfo candidateInfo = new MethodCandidateInfo(method, PsiSubstitutor.EMPTY, false, false, argumentList, null,
                                                               argumentList.getExpressionTypes(), null);
    return candidateInfo.getSubstitutor();
  }

  /**
   * @return true when the argument count and the argument types of the call can fit the method
   */
  static boolean canMatchCall(@NotNull PsiMethod method, @NotNull PsiExpressionList argumentList) {
    int argumentCount = argumentList.getExpressionCount();
    if (!PsiUtil.isJavaToken(argumentList.getLastChild(), JavaTokenType.RPARENTH)) {
      // the user still writes the arguments, so the count is only the lower bound
      return method.isVarArgs() || method.getParameterList().getParametersCount() >= argumentCount;
    }
    return ApproximateResolver.canHaveArgCount(method, argumentCount) && isLooselyApplicable(method, argumentList);
  }

  /**
   * Compares the argument types with the parameter types, without type inference. The check accepts an argument which
   * it cannot decide: an argument with an unknown type, a lambda, a method reference, and a parameter which stays
   * a type parameter.
   *
   * @return true when each argument the check can decide fits the parameter
   */
  private static boolean isLooselyApplicable(@NotNull PsiMethod method, @NotNull PsiExpressionList argumentList) {
    PsiType[] argumentTypes = argumentList.getExpressionTypes().clone();
    boolean[] undecided = new boolean[argumentTypes.length];
    for (int i = 0; i < argumentTypes.length; i++) {
      PsiType argumentType = argumentTypes[i];
      if (argumentType == null || argumentType instanceof PsiLambdaExpressionType || argumentType instanceof PsiMethodReferenceType) {
        undecided[i] = true;
        // PsiUtil.getApplicabilityLevel rejects a null type before it calls the checker, so it needs a placeholder
        argumentTypes[i] = PsiTypes.nullType();
      }
    }
    PsiUtil.ApplicabilityChecker checker = (parameterType, argumentType, _, argumentIndex) -> {
      if (argumentIndex >= 0 && argumentIndex < undecided.length && undecided[argumentIndex]) return true;
      return isLooselyAssignable(parameterType, argumentType);
    };
    PsiSubstitutor substitutor = inferSubstitutor(method, argumentList);
    int level = PsiUtil.getApplicabilityLevel(method, substitutor, argumentTypes, PsiUtil.getLanguageLevel(argumentList),
                                              true, true, checker);
    return level != MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
  }

  private static boolean isLooselyAssignable(@Nullable PsiType parameterType, @Nullable PsiType argumentType) {
    if (parameterType == null || argumentType == null) return true;
    // the inference did not resolve the type parameter, so the check cannot decide the argument
    if (PsiUtil.resolveClassInClassTypeOnly(parameterType) instanceof PsiTypeParameter) return true;
    PsiType parameterErasure = TypeConversionUtil.erasure(parameterType);
    PsiType argumentErasure = TypeConversionUtil.erasure(argumentType);
    if (parameterErasure == null || argumentErasure == null) return true;
    return TypeConversionUtil.isAssignable(parameterErasure, argumentErasure, true);
  }
}
