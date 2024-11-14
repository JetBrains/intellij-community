// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * List of attributes (for example {@code file} or {@code class}) of a given doc tag.
 *
 * @see PsiSnippetDocTag
 */
public interface PsiSnippetAttributeList extends PsiElement {
  /**
   * @return array of name-value pairs of snippet tag.
   */
  PsiSnippetAttribute @NotNull [] getAttributes();

  /**
   * @param name name of the attribute to find
   * @return the first instance of attribute having a given name; null if no such attribute found
   */
  @Nullable PsiSnippetAttribute getAttribute(@NotNull String name);
}
