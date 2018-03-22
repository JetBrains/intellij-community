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

import org.jetbrains.annotations.NotNull;

/**
 * JavaResolveResult holds additional information that is obtained
 * when Java references are being resolved.
 *
 * @author ik, dsl
 * @see PsiCall#resolveMethodGenerics()
 */
public interface JavaResolveResult extends ResolveResult {
  JavaResolveResult[] EMPTY_ARRAY = new JavaResolveResult[0];

  /**
   * Substitutor providing values of type parameters occurring in {@link #getElement()}.
   */
  @NotNull
  PsiSubstitutor getSubstitutor();

  boolean isPackagePrefixPackageReference();

  /**
   * @return true if {@link #getElement()} is accessible from reference.
   */
  boolean isAccessible();

  boolean isStaticsScopeCorrect();

  /**
   * @return scope in the reference's file where the reference has been resolved,
   *         {@code null} for qualified and local references.
   */
  PsiElement getCurrentFileResolveScope();

  JavaResolveResult EMPTY = new JavaResolveResult() {
    @Override public PsiElement getElement() { return null; }
    @NotNull
    @Override public PsiSubstitutor getSubstitutor() { return PsiSubstitutor.EMPTY; }
    @Override public boolean isValidResult() { return false; }
    @Override public boolean isAccessible() { return false; }
    @Override public boolean isStaticsScopeCorrect() { return false; }
    @Override public PsiElement getCurrentFileResolveScope() { return null; }
    @Override public boolean isPackagePrefixPackageReference() { return false; }
  };
}