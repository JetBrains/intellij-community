// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * List of attributes (for example {@code file} or {@code class}) of a given doc tag.
 *
 * @see PsiSnippetDocTag
 */
@ApiStatus.Experimental
public interface PsiSnippetAttributeList extends PsiElement {
  /**
   * @return list of name-value pairs of snippet tag.
   */
  PsiSnippetAttribute @NotNull [] getAttributes();
}
