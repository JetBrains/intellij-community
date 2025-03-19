// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an unnamed pattern (single '_' inside deconstruction pattern, like {@code R(_)}).
 * Not to be confused with type pattern with unnamed variable (like {@code R(int _)})
 */
@ApiStatus.Experimental
public interface PsiUnnamedPattern extends PsiPrimaryPattern {
  /**
   * @return implicit type element (empty)
   */
  @NotNull PsiTypeElement getTypeElement();
}
