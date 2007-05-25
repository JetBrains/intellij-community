/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for breaking a file into a sequence of tokens.
 * @see LexerBase for certain methods' implementation
 */
public interface Lexer {
  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset 0 and
   * terminated (EOFed) at offset <code>buffer.length</code>. Internal lexer state is supposed to be initial.
   * YY_INITIAL for JLex and JFlex generated lexer.
   * @deprecated Use start(CharSequence, startOffset,endOffset, state);
   * @param buffer character data for lexing.
   */
  @Deprecated void start(char[] buffer);

  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset <code>startOffset</code> and
   * terminated (EOFed) at offset <code>endOffset</code>. Internal lexer state is supposed to be initial. YY_INITIAL for JLex and JFlex generated
   * lexer.
   *
   * @param buffer      character data for lexing.
   * @param startOffset offset to start lexing from
   * @param endOffset   offset to stop lexing at
   * @deprecated Use start(CharSequence, startOffset,endOffset, state);
   */
  @Deprecated void start(char[] buffer, int startOffset, int endOffset);

  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset <code>startOffset> and
   * terminated (EOFed) at offset <code>endOffset</code>. Internal lexer state is supposed to be <code>initialState</code>. It is guaranteed
   * that the value of initialState has been returned by {@link #getState()} method of this <code>Lexer</code> at condition <code>startOffset=getTokenStart()</code>
   * This method is used to incrementally relex changed characters using lexing data acquired from this particular lexer sometime in the past.
   *
   * @param buffer       character data for lexing.
   * @param startOffset  offset to start lexing from
   * @param endOffset    offset to stop lexing at
   * @param initialState the initial state of the lexer.
   * @deprecated Use start(CharSequence, startOffset,endOffset, state);
   */
  @Deprecated void start(char[] buffer, int startOffset, int endOffset, int initialState);

  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Internal lexer state is supposed to be <code>initialState</code>. It is guaranteed
   * that the value of initialState has been returned by {@link #getState()} method of this <code>Lexer</code> at condition <code>startOffset=getTokenStart()</code>
   * This method is used to incrementally relex changed characters using lexing data acquired from this particular lexer sometime in the past.
   *
   * @param buffer       character data for lexing.
   * @param startOffset  offset to start lexing from
   * @param endOffset    offset to stop lexing at
   * @param initialState the initial state of the lexer.
   * @since IDEA 7
   */
  void start(CharSequence buffer, int startOffset, int endOffset, int initialState);

  /**
   * Returns the current state of the lexer.
   *
   * @return the lexer state.
   */
  int getState();

  /**
   * Returns the token at the current position of the lexer or <code>null</code> if lexing is finished.
   *
   * @return the current token.
   */
  @Nullable
  IElementType getTokenType();

  /**
   * Returns the start offset of the current token.
   *
   * @return the current token start offset.
   */
  int getTokenStart();

  /**
   * Returns the end offset of the current token.
   *
   * @return the current token end offset.
   */

  int getTokenEnd();

  /**
   * Advances the lexer to the next token.
   */
  void advance();

  /**
   * Returns the current position and state of the lexer.
   *
   * @return the lexer position and state.
   */
  LexerPosition getCurrentPosition();

  /**
   * Restores the lexer to the specified state and position.
   *
   * @param position the state and position to restore to.
   */
  void restore(LexerPosition position);

  /**
   * Returns the buffer over which the lexer is running. This method should return the
   * same buffer instance which was passed to the <code>start()</code> method.
   * @deprecated Use getBufferSequence
   * @return the lexer buffer.
   */

  @Deprecated char[] getBuffer();

  /**
   * Returns the buffer sequence over which the lexer is running. This method should return the
   * same buffer instance which was passed to the <code>start()</code> method.
   * @return the lexer buffer.
   * @since IDEA 7
   */
  CharSequence getBufferSequence();

  /**
   * Returns the offset at which the lexer will stop lexing. This method should return
   * the length of the buffer or the value passed in the <code>endOffset</code> parameter
   * to the <code>start()</code> method.
   *
   * @return the lexing end offset
   */
  int getBufferEnd();
}
