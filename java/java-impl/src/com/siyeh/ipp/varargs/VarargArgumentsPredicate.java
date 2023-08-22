// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.varargs;

import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class VarargArgumentsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof PsiExpressionList argumentList)) {
      return false;
    }
    final PsiElement parent = argumentList.getParent();
    if (!(parent instanceof PsiCall call)) {
      return false;
    }
    final JavaResolveResult resolveResult = call.resolveMethodGenerics();
    if (!resolveResult.isValidResult() || !(resolveResult instanceof MethodCandidateInfo candidateInfo) ||
        candidateInfo.getApplicabilityLevel() != MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
      return false;
    }
    final PsiMethod method = candidateInfo.getElement();
    if (!method.isVarArgs()) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiType lastParameterType = PsiTypesUtil.getParameterType(parameters, parameters.length - 1, true);
    final PsiType substitutedType = substitutor.substitute(lastParameterType);
    if (substitutedType instanceof PsiCapturedWildcardType capturedWildcardType) {
      if (!capturedWildcardType.getWildcard().isSuper()) {
        // red code
        return false;
      }
    }

    if (arguments.length != parameters.length) {
      return true;
    }
    final PsiExpression lastExpression = arguments[arguments.length - 1];
    if (ExpressionUtils.isNullLiteral(lastExpression)) {
      // a single null argument is not wrapped in an array
      // on a vararg method call, but just passed as a null value
      return false;
    }
    final PsiType lastArgumentType = lastExpression.getType();
    if (!(lastArgumentType instanceof PsiArrayType arrayType)) {
      return true;
    }
    final PsiType type = arrayType.getComponentType();
    return !substitutedType.isAssignableFrom(type);
  }
}