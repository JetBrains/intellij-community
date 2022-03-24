// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents elements starting from ':' (inclusive) and until '}' (exclusive) in @snippet javadoc tag.
 * @see PsiSnippetDocTag
 */
@ApiStatus.Experimental
public interface PsiSnippetDocTagBody extends PsiElement {
  /**
   * @return elements which text makes up snippet (without leading *)
   */
  PsiElement @NotNull [] getContent();
}
