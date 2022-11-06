/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface PsiQualifiedReference extends PsiReference {
  /**
   * Returns the qualifier of the reference (the element representing the content up to the
   * last period).
   *
   * @return the qualifier, or null if the reference is not qualified.
   */
  @Nullable
  PsiElement getQualifier();

  /**
   * Returns the text of the reference not including its qualifier.
   *
   * @return the non-qualified text of the reference, or null if the reference
   * element is incomplete.
   */
  @Nullable @NlsSafe
  String getReferenceName();
}
