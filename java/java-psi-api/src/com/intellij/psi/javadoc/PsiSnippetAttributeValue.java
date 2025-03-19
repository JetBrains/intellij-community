// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an attribute value for a snippet attribute
 * 
 * @see PsiSnippetAttributeValue
 */
public interface PsiSnippetAttributeValue extends PsiElement {
  /**
   * Returns the content of the attribute value (without quotes, if any)
   */
  @Contract(pure = true)
  @NotNull String getValue();
}
