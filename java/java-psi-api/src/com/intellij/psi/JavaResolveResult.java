// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    @Override
    public @NotNull PsiSubstitutor getSubstitutor() { return PsiSubstitutor.EMPTY; }
    @Override public boolean isValidResult() { return false; }
    @Override public boolean isAccessible() { return false; }
    @Override public boolean isStaticsScopeCorrect() { return false; }
    @Override public PsiElement getCurrentFileResolveScope() { return null; }
    @Override public boolean isPackagePrefixPackageReference() { return false; }
  };
}