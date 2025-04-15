// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a token inside a JavaDoc comment.
 *
 * @author Mike
 */
public interface PsiDocToken extends PsiElement {
  /**
   * Returns the element type of this token.
   */
  IElementType getTokenType();

  /**
   * Checks if the specified element is a {@code PsiDocToken} and the token type matches the specified
   * token type.
   *
   * @param element  the element to check
   * @param tokenType  the token type the {@code PsiDocToken} needs to have
   * @return true, if the specified element is a PsiDocToken with the specified element type, false otherwise.
   */
  static boolean isDocToken(@Nullable PsiElement element, IElementType tokenType) {
    return element instanceof PsiDocToken && ((PsiDocToken)element).getTokenType() == tokenType;
  }

  /**
   * Checks if the specified element is a {@code PsiDocToken} and the token type matches one of the specified
   * token types.
   *
   * @param element  the element to check
   * @param types  the token types the {@code PsiDocToken} is allowed to have
   * @return {@code true}, if the specified element is a PsiDocToken with one of the specified element types, {@code false} otherwise.
   */
  static boolean isDocToken(@Nullable PsiElement element, @NotNull TokenSet types) {
    return element instanceof PsiDocToken && types.contains(((PsiDocToken)element).getTokenType());
  }
}
