/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
public interface PsiLambdaExpression extends PsiExpression {
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

  /**
   * @return SAM type the lambda expression corresponds to
   *         null when no SAM type could be found
   */
  @Nullable
  PsiType getFunctionalInterfaceType();

  boolean isVoidCompatible();

  /**
   * @return true when lambda declares parameter types explicitly
   */
  boolean hasFormalParameterTypes();
}
