// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolResolveResult;
import com.intellij.model.SymbolService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents the result of resolving a {@link com.intellij.psi.PsiPolyVariantReference}.
 *
 * @see com.intellij.psi.PsiElementResolveResult
 */
public interface ResolveResult extends SymbolResolveResult {
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

  @NotNull
  @Override
  default Symbol getTarget() {
    PsiElement element = Objects.requireNonNull(getElement(), "Don't access this property on empty results");
    return SymbolService.adaptPsiElement(element);
  }
}
