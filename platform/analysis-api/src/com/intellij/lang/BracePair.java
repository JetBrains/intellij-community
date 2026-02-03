// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a single pair of braces which need to be matched when editing code in a custom language.
 *
 * @author max
 * @see PairedBraceMatcher
 */

public final class BracePair {
  private final @NotNull IElementType myLeftBrace;
  private final @NotNull IElementType myRightBrace;
  private final boolean myStructural;

  /**
   * Creates a new brace pair instance.
   *
   * @param leftBrace      the lexer token type for the left brace in the pair.
   * @param rightBrace     the lexer token type for the right brace in the pair.
   * @param structural     if true, the brace is considered structural (see {@link #isStructural()} for details)
   */
  public BracePair(@NotNull IElementType leftBrace, @NotNull IElementType rightBrace, final boolean structural) {
    myLeftBrace = leftBrace;
    myRightBrace = rightBrace;
    myStructural = structural;
  }

  /**
   * Returns the lexer token type for the left brace in the pair.
   *
   * @return token type
   */
  public @NotNull IElementType getLeftBraceType() {
    return myLeftBrace;
  }

  /**
   * Returns the lexer token type for the right brace in the pair.
   *
   * @return token type
   */
  public @NotNull IElementType getRightBraceType() {
    return myRightBrace;
  }

  /**
   * Returns true if the brace is structural. Structural braces have higher priority than regular braces:
   * they are matched with each other even if there are unmatched braces of other types between them,
   * and an opening non-structural brace is not matched with a closing one if one of them is outside a pair
   * of matched structural braces and another is outside. In Java code, the curly braces are structural.
   *
   * @return true if the brace is structural, false otherwise.
   */
  public boolean isStructural() {
    return myStructural;
  }

  @Override
  public @NonNls String toString() {
    return "BracePair{" +
           "myLeftBrace=" + myLeftBrace +
           ", myRightBrace=" + myRightBrace +
           ", myStructural=" + myStructural +
           '}';
  }
}
