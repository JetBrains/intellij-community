// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;

/**
 * Represents a single token in a Java file (the lowest-level element in the Java PSI tree).
 */
public interface PsiJavaToken extends PsiElement {
  /**
   * Returns the type of the token.
   *
   * @return the token type.
   */
  IElementType getTokenType();
}