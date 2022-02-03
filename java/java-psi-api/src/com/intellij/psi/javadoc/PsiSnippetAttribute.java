// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents name-value pair in @snippet tag.
 * @see PsiSnippetDocTag
 */
@ApiStatus.Experimental
public interface PsiSnippetAttribute extends PsiElement {
  PsiSnippetAttribute[] EMPTY_ARRAY = new PsiSnippetAttribute[0];

  /**
   * @return name element of this name-value pair.
   */
  @NotNull PsiElement getNameIdentifier();

  /**
   * @return name of this name-value pair.
   */
  @NotNull String getName();

  /**
   * @return value of this name-value pair or null if absent.
   */
  @Nullable PsiElement getValue();
}
