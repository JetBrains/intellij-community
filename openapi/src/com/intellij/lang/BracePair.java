/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * Defines a single pair of braces which need to be matched when editing code in a custom language.
 * @author max
 * @see PairedBraceMatcher
 */

public class BracePair {
  private IElementType myLeftBrace;
  private char myLeftBraceChar;
  private IElementType myRightBrace;
  private char myRightBraceChar;
  private boolean myStructural;

  /**
   * Creates a new brace pair instance.
   * @param leftBraceChar the character for the left brace in the pair.
   * @param leftBrace the lexer token type for the left brace in the pair.
   * @param rightBraceChar the character for the right brace in the pair.
   * @param rightBrace the lexer token type for the right brace in the pair.
   * @param structural if true, the brace is considered structural (see {@link #isStructural()} for details)
   */
  public BracePair(char leftBraceChar, final IElementType leftBrace,char rightBraceChar,final IElementType rightBrace, final boolean structural) {
    myLeftBraceChar = leftBraceChar;
    myLeftBrace = leftBrace;
    myRightBraceChar = rightBraceChar;
    myRightBrace = rightBrace;
    myStructural = structural;
  }

  /**
   * Returns the lexer token type for the left brace in the pair.
   * @return token type
   */
  public IElementType getLeftBraceType() {
    return myLeftBrace;
  }

  /**
   * Returns the lexer token type for the right brace in the pair.
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
   * @return true if the brace is structural, false otherwise.
   */
  public boolean isStructural() {
    return myStructural;
  }

  /**
   * Returns the character for the left brace in the pair.
   * @return brace character
   */
  public char getLeftBraceChar() {
    return myLeftBraceChar;
  }

  /**
   * Returns the character for the right brace in the pair.
   * @return brace character
   */
  public char getRightBraceChar() {
    return myRightBraceChar;
  }
}
