// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.model.psi.UrlReferenceHost;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a comment in a code.
 */
public interface PsiComment extends PsiElement, UrlReferenceHost {
  /**
   * Returns the token type of the comment (like {@code JavaTokenType.END_OF_LINE_COMMENT} or {@code JavaTokenType.C_STYLE_COMMENT}).
   */
  @NotNull
  IElementType getTokenType();
}