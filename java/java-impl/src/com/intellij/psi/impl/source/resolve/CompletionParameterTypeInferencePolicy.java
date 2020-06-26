// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;

/**
 * @author yole
 */
public final class CompletionParameterTypeInferencePolicy extends ProcessCandidateParameterTypeInferencePolicy {
  public static final CompletionParameterTypeInferencePolicy INSTANCE = new CompletionParameterTypeInferencePolicy();

  private CompletionParameterTypeInferencePolicy() {
  }

  @Override
  public PsiType getDefaultExpectedType(PsiCallExpression methodCall) {
    ExpectedTypeInfo expectedType = ExpectedTypesProvider.getSingleExpectedTypeForCompletion(methodCall);
    return expectedType == null ? PsiType.NULL : expectedType.getType();
  }

  @Override
  public Pair<PsiType, ConstraintType> getInferredTypeWithNoConstraint(PsiManager psiManager, PsiType superType) {
    if (!(superType instanceof PsiWildcardType)) {
      return new Pair<>(PsiWildcardType.createExtends(psiManager, superType), ConstraintType.EQUALS);
    }
    else {
      return Pair.create(superType, ConstraintType.SUBTYPE);
    }
  }

  @Override
  public boolean inferRuntimeExceptionForThrownBoundWithNoConstraints() {
    return false;
  }

  @Override
  public PsiType adjustInferredType(PsiManager manager, PsiType guess, ConstraintType constraintType) {
    if (guess != null && !(guess instanceof PsiWildcardType) && guess != PsiType.NULL) {
      if (constraintType == ConstraintType.SUPERTYPE) return PsiWildcardType.createExtends(manager, guess);
      else if (constraintType == ConstraintType.SUBTYPE) return PsiWildcardType.createSuper(manager, guess);
    }
    return guess;
  }

  @Override
  public boolean isVarargsIgnored() {
    return true;
  }

  @Override
  public boolean inferLowerBoundForFreshVariables() {
    return true;
  }

  @Override
  public boolean requestForBoxingExplicitly() {
    return true;
  }
}
