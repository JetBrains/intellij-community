// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.pom.PomResolveResult;
import com.intellij.pom.PomTarget;
import com.intellij.pom.references.PomService;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of resolving a {@link com.intellij.psi.PsiPolyVariantReference}.
 *
 * @see com.intellij.psi.PsiElementResolveResult
 */
public interface ResolveResult extends PomResolveResult {
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

  @Nullable
  @Override
  default PomTarget getTarget() {
    PsiElement element = getElement();
    return element == null ? null : PomService.convertToPom(element);
  }
}
