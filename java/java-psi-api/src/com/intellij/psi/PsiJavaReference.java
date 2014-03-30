/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a reference found in Java code.
 */
public interface PsiJavaReference extends PsiPolyVariantReference {
  /**
   * Passes all variants to which the reference may resolve to the specified
   * processor.
   *
   * @param processor the processor accepting the variants
   */
  void processVariants(@NotNull PsiScopeProcessor processor);

  /**
   * Resolves the reference and returns the result as a {@link JavaResolveResult}
   * instead of a plain {@link PsiElement}.
   *
   * @param incompleteCode if true, the code in the context of which the reference is
   * being resolved is considered incomplete, and the method may return an invalid
   * result.
   * @return the result of the resolve.
   */
  @NotNull
  JavaResolveResult advancedResolve(boolean incompleteCode);

  @Override
  @NotNull
  JavaResolveResult[] multiResolve(boolean incompleteCode);
}
