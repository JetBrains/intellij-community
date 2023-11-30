// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for PSI elements which do not actually exist in code (like JSP classes and JSP
 * holder methods).
 */
public interface SyntheticElement {
  /**
   * @param copy copy file to find the same element in
   * @return same synthetic element inside the copy of the containing file
   */
  @ApiStatus.Experimental
  default @NotNull PsiElement findSameElementInCopy(@NotNull PsiFile copy) {
    throw new UnsupportedOperationException("LightElement " + getClass().getName() + " has no strategy to get its copy");
  }
}
