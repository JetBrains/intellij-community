// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents an attribute value for a snippet attribute
 * 
 * @see PsiSnippetAttributeValue
 */
@ApiStatus.Experimental
public interface PsiSnippetAttributeValue extends PsiElement {
  /**
   * Returns the content of the attribute value (without quotes, if any)
   */
  String getValue();
}
