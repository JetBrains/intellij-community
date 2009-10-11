/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * Defines a single pair of braces which need to be matched when editing code in a custom language.
 *
 * @author max
 * @see PairedBraceMatcher
 */

public class BracePair {
  private final IElementType myLeftBrace;
  private final IElementType myRightBrace;
  private final boolean myStructural;

  /**
   * Creates a new brace pair instance.
   *
   * @param leftBrace      the lexer token type for the left brace in the pair.
   * @param rightBrace     the lexer token type for the right brace in the pair.
   * @param structural     if true, the brace is considered structural (see {@link #isStructural()} for details)
   */
  public BracePair(final IElementType leftBrace, final IElementType rightBrace, final boolean structural) {
    myLeftBrace = leftBrace;
    myRightBrace = rightBrace;
    myStructural = structural;
  }

  /**
   * Returns the lexer token type for the left brace in the pair.
   *
   * @return token type
   */
  public IElementType getLeftBraceType() {
    return myLeftBrace;
  }

  /**
   * Returns the lexer token type for the right brace in the pair.
   *
   * @return token type
   */
  public IElementType getRightBraceType() {
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
}
