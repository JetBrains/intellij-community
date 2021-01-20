// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of resolving a {@link PsiPolyVariantReference}.
 *
 * @see PsiElementResolveResult
 */
public interface ResolveResult {

  /**
   * The empty array of PSI resolve results which can be reused to avoid unnecessary allocations.
   */
  ResolveResult[] EMPTY_ARRAY = new ResolveResult[0];

  /**
   * Returns the result of the resolve.
   *
   * @return an element the reference is resolved to.
   */
  @Nullable
  PsiElement getElement();

  /**
   * Checks if the reference was resolved to a valid element.
   *
   * @return true if the resolve encountered no problems
   */
  boolean isValidResult();
}
