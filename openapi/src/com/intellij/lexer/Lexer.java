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
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Interface for breaking a file into a sequence of tokens.
 */

public interface Lexer {
  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset 0 and
   * terminated (EOFed) at offset <code>buffer.length</code>. Internal lexer state is supposed to be initial.
   * YY_INITIAL for JLex and JFlex generated lexer.
   * @param buffer character data for lexing.
   */
  void start(char[] buffer);

  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset <code>startOffset</code> and
   * terminated (EOFed) at offset <code>endOffset</code>. Internal lexer state is supposed to be initial. YY_INITIAL for JLex and JFlex generated
   * lexer.
   * @param buffer character data for lexing.
   * @param startOffset offset to start lexing from
   * @param endOffset offset to stop lexing at
   */
  void start(char[] buffer, int startOffset, int endOffset);

  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset <code>startOffset> and
   * terminated (EOFed) at offset <code>endOffset</code>. Internal lexer state is supposed to be <code>initialState</code>. It is guaranteed
   * that the value of initialState has been returned by {@link #getState()} method of this <code>Lexer</code> at condition <code>startOffset=getTokenStart()</code>
   * This method is used to incrementally relex changed characters using lexing data acquired from this particular lexer sometime in the past.
   *
   * @param buffer character data for lexing.
   * @param startOffset offset to start lexing from
   * @param endOffset offset to stop lexing at
   * @param initialState the initial state of the lexer.
   */
  void start(char[] buffer, int startOffset, int endOffset, int initialState);

  /**
   * Returns the current state of the lexer.
   * @return the lexer state.
   */
  int getState();

  /**
   * Returns the token at the current position of the lexer.
   * @return the current token.
   */
  IElementType getTokenType();

  /**
   * Returns the start offset of the current token.
   * @return the current token start offset.
   */
  int getTokenStart();

  /**
   * Returns the end offset of the current token.
   * @return the current token end offset.
   */

  int getTokenEnd();

  /**
   * Advances the lexer to the next token.
   */
  void advance();

  /**
   * Returns the current position and state of the lexer.
   * @return the lexer position and state.
   */
  LexerPosition getCurrentPosition();

  /**
   * Restores the lexer to the specified state and position.
   * @param position the state and position to restore to.
   */
  void restore(LexerPosition position);

  /**
   * Returns the buffer over which the lexer is running.
   * @return the lexer buffer.
   */

  char[] getBuffer();

  /**
   * Returns the offset at which the lexer will stop lexing.
   * @return the lexing end offset
   */
  int getBufferEnd();
}
