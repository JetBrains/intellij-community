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

import org.jetbrains.annotations.Nullable;

/**
 * Represents a method or constructor reference.
 */
public interface PsiMethodReferenceExpression extends PsiReferenceExpression {
  /**
   * Returns the type element used as the qualifier of the reference.
   *
   * @return the qualifier, or null if the reference is qualified by expression.
   */
  @Nullable
  PsiTypeElement getQualifierType();
  
  /**
   * @return SAM type the method reference expression corresponds to
   *         null when no SAM type could be found
  */
  @Nullable
  PsiType getFunctionalInterfaceType();

  boolean isExact();
}
