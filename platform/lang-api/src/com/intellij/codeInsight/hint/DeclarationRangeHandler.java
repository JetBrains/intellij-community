// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Returns the subset of the text range of the specified element which is considered its declaration.
 * For example, the declaration range of a method includes its modifiers, return type, name and
 * parameter list.
 *
 * Used by "Context info" action.
 */
public interface DeclarationRangeHandler<T extends PsiElement> {
  /**
   * Returns the declaration range for the specified container or null if it doesn't contain one
   * @param container the container
   * @return the declaration range for it.
   */
  @Nullable TextRange getDeclarationRange(@NotNull T container);
}
