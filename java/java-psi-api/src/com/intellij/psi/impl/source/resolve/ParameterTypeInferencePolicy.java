// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public abstract class ParameterTypeInferencePolicy {
  public abstract @Nullable Pair<PsiType, ConstraintType> inferTypeConstraintFromCallContext(PsiExpression innerMethodCall,
                                                                                             PsiExpressionList parent,
                                                                                             PsiCallExpression contextCall,
                                                                                             PsiTypeParameter typeParameter);

  public abstract PsiType getDefaultExpectedType(PsiCallExpression methodCall);

  public abstract Pair<PsiType, ConstraintType> getInferredTypeWithNoConstraint(PsiManager manager, PsiType superType);

  public boolean inferRuntimeExceptionForThrownBoundWithNoConstraints() {
    return true;
  }

  public abstract PsiType adjustInferredType(PsiManager manager, PsiType guess, ConstraintType second);

  public boolean isVarargsIgnored() {
    return false;
  }

  /**
   * For infinite type declarations, like {@code Foo<T extends Foo<T>>}, inference introduces fake fresh "fixed" type parameters.
   * These fresh parameters respect constraints, created during inference session. For completion, it makes sense to define lower bounds
   * even if no appropriate constraints were detected, as probably the corresponding argument is currently completed. 
   */
  public boolean inferLowerBoundForFreshVariables() {
    return false;
  }

  /**
   * Workaround for inference < 1.8. 
   * 
   * Boxed type is used for inference as it may be specified as type argument explicitly later for completion 
   * {@link com.intellij.codeInsight.completion.JavaMethodCallElement#setInferenceSubstitutorFromExpectedType(PsiElement, PsiType)},
   * but should not be used for normal inference due to javac bug
   */
  public boolean requestForBoxingExplicitly() {
    return false;
  }
}
