/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;

/**
 * @author yole
 */
public class CompletionParameterTypeInferencePolicy extends ProcessCandidateParameterTypeInferencePolicy {
  public static final CompletionParameterTypeInferencePolicy INSTANCE = new CompletionParameterTypeInferencePolicy();

  private CompletionParameterTypeInferencePolicy() {
  }

  @Override
  public PsiType getDefaultExpectedType(PsiCallExpression methodCall) {
    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(methodCall, true);
    if (expectedTypes.length > 0) {
      return expectedTypes[0].getType();
    }
    return PsiType.NULL;
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
  public PsiType adjustInferredType(PsiManager manager, PsiType guess, ConstraintType constraintType) {
    if (guess != null && !(guess instanceof PsiWildcardType)) {
      if (constraintType == ConstraintType.SUPERTYPE) return PsiWildcardType.createExtends(manager, guess);
      else if (constraintType == ConstraintType.SUBTYPE) return PsiWildcardType.createSuper(manager, guess);
    }
    return guess;
  }

  @Override
  public boolean isVarargsIgnored() {
    return true;
  }
}
