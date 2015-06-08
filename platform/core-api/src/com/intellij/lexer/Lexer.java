/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for breaking a file into a sequence of tokens.
 *
 * @see LexerBase for certain methods' implementation
 */
public abstract class Lexer {

  /**
   * Prepare for lexing character data from {@code buffer} passed. Internal lexer state is supposed to be {@code initialState}. It is guaranteed
   * that the value of initialState is the same as returned by {@link #getState()} method of this lexer at condition {@code startOffset=getTokenStart()}.
   * This method is used to incrementally re-lex changed characters using lexing data acquired from this particular lexer sometime in the past.
   *
   * @param buffer       character data for lexing.
   * @param startOffset  offset to start lexing from
   * @param endOffset    offset to stop lexing at
   * @param initialState the initial state of the lexer.
   * @since IDEA 7
   */
  public abstract void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState);

  public final void start(@NotNull CharSequence buf, int start, int end) {
    start(buf, start, end, 0);
  }

  public final void start(@NotNull CharSequence buf) {
    start(buf, 0, buf.length(), 0);
  }

  @NotNull
  public CharSequence getTokenSequence() {
    return getBufferSequence().subSequence(getTokenStart(), getTokenEnd());
  }

  @NotNull
  public String getTokenText() {
    return getTokenSequence().toString();
  }

  /**
   * Returns the current state of the lexer.
   *
   * @return the lexer state.
   */
  public abstract int getState();

  /**
   * Returns the token at the current position of the lexer or <code>null</code> if lexing is finished.
   *
   * @return the current token.
   */
  @Nullable
  public abstract IElementType getTokenType();

  /**
   * Returns the start offset of the current token.
   *
   * @return the current token start offset.
   */
  public abstract int getTokenStart();

  /**
   * Returns the end offset of the current token.
   *
   * @return the current token end offset.
   */

  public abstract int getTokenEnd();

  /**
   * Advances the lexer to the next token.
   */
  public abstract void advance();

  /**
   * Returns the current position and state of the lexer.
   *
   * @return the lexer position and state.
   */
  @NotNull
  public abstract LexerPosition getCurrentPosition();

  /**
   * Restores the lexer to the specified state and position.
   *
   * @param position the state and position to restore to.
   */
  public abstract void restore(@NotNull LexerPosition position);

  /**
   * Returns the buffer sequence over which the lexer is running. This method should return the
   * same buffer instance which was passed to the <code>start()</code> method.
   *
   * @return the lexer buffer.
   * @since IDEA 7
   */
  @NotNull
  public abstract CharSequence getBufferSequence();

  /**
   * Returns the offset at which the lexer will stop lexing. This method should return
   * the length of the buffer or the value passed in the <code>endOffset</code> parameter
   * to the <code>start()</code> method.
   *
   * @return the lexing end offset
   */
  public abstract int getBufferEnd();
}
