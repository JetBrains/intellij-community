// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents elements between '(' and ')' (inclusive) in {@link PsiDeconstructionPattern}.
 */
@ApiStatus.Experimental
public interface PsiDeconstructionList extends PsiElement {
  @NotNull
  PsiPattern @NotNull [] getDeconstructionComponents();
}
