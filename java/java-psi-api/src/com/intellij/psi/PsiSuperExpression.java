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

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code super} expression in a superclass member reference (but not in a super constructor call,
 * where the {@code super} qualifier is represented as a {@link PsiKeyword} wrapped in a {@link PsiReferenceExpression}).
 */
public interface PsiSuperExpression extends PsiQualifiedExpression {
  /**
   * Returns an expression representing a type name qualifying the {@code super} expression.
   */
  @Override
  @Nullable
  PsiJavaCodeReferenceElement getQualifier();
}