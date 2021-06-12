// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class DefaultParameterTypeInferencePolicy extends ParameterTypeInferencePolicy {
  public static final DefaultParameterTypeInferencePolicy INSTANCE = new DefaultParameterTypeInferencePolicy();
  
  @Nullable
  @Override
  public Pair<PsiType, ConstraintType> inferTypeConstraintFromCallContext(PsiExpression innerMethodCall,
                                                                          PsiExpressionList parent,
                                                                          PsiCallExpression contextCall,
                                                                          PsiTypeParameter typeParameter) {
    return null;
  }

  @Override
  public PsiType getDefaultExpectedType(PsiCallExpression methodCall) {
    return PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope());
  }

  @Override
  public Pair<PsiType, ConstraintType> getInferredTypeWithNoConstraint(PsiManager manager, PsiType superType) {
    return Pair.create(superType, ConstraintType.SUBTYPE);
  }

  @Override
  public PsiType adjustInferredType(PsiManager manager, PsiType guess, ConstraintType constraintType) {
    return guess;
  }
}
