/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java lambda expression.
 */
public interface PsiLambdaExpression extends PsiFunctionalExpression, PsiParameterListOwner {
  /**
   * Returns this lambda expression's parameter list.
   *
   * @return parameter list.
   */
  @NotNull
  PsiParameterList getParameterList();

  /**
   * Returns PSI element representing lambda expression body: {@link PsiCodeBlock}, {@link PsiExpression},
   * or null if the expression is incomplete.
   *
   * @return lambda expression body.
   */
  @Nullable
  PsiElement getBody();

  boolean isVoidCompatible();
  boolean isValueCompatible();

  /**
   * @return true when lambda declares parameter types explicitly
   */
  boolean hasFormalParameterTypes();

  /**
   * A lambda expression (§15.27) is potentially compatible with a functional interface type (§9.8) if all of the following are true:
   *   The arity of the target type's function type is the same as the arity of the lambda expression.
   *   If the target type's function type has a void return, then the lambda body is either a statement expression (§14.8) or a void-compatible block (§15.27.2).
   *   If the target type's function type has a (non-void) return type, then the lambda body is either an expression or a value-compatible block (§15.27.2).
   */
  boolean isPotentiallyCompatible(PsiType left);
}
