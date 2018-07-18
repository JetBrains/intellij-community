/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class ParameterTypeInferencePolicy {
  @Nullable
  public abstract Pair<PsiType, ConstraintType> inferTypeConstraintFromCallContext(PsiExpression innerMethodCall,
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
}
