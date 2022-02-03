// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The content of the snippet tag (attributes and inline body if present).
 * @see PsiSnippetDocTag
 */
@ApiStatus.Experimental
public interface PsiSnippetDocTagValue extends PsiDocTagValue {
  /**
   * @return list of name-value pairs of the snippet tag.
   */
  @NotNull PsiSnippetAttributeList getAttributeList();

  /**
   * @return body (content) of the snippet tag if there is a body
   */
  @Nullable PsiSnippetDocTagBody getBody();
}
