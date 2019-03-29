/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is suitable for providing even distribution for restartable states in highlighting lexer implementation.
 * Perfect highlighting lexer should be able to restart quickly from any context.
 * Sometimes it's not enough to have only one restartable state in lexer logic.
 * Implement {@link #isRestartableState(int)} to provide several restartable states.
 * In some cases some additonal information is needed to restart from non-trivial state.
 * {@link #start(CharSequence, int, int, int, Iterable)} implementation helps quickly retrieve information from preceding part of the file
 * to restore lexer properly
 * {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter#documentChanged(DocumentEvent)}
 */
public interface RestartableLexer {
  /**
   * Returns the start state of the lexer.
   *
   * @return the lexer state.
   */
  int getStartState();


  /**
   * Returns the default restartable state of the lexer.
   *
   * @return the lexer state.
   */
  int getRestartableState();

  /**
   * Packs tokenType and lexer state in int that is used in {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter#mySegments}
   *
   * @param tokenType lexer current token type
   * @param state     lexer current state
   * @return packed lexer state and tokenType in data
   */
  default int packData(IElementType tokenType, int state) {
    return ((state & 0xFFFF) << 16) | (tokenType.getIndex() & 0xffff);
  }

  /**
   * Unpacks token type from segment data returned by
   * {@link com.intellij.openapi.editor.ex.util.SegmentArrayWithData#getSegmentData(int)}
   *
   * @param data to unpack
   * @return element type stored in data
   * @throws IndexOutOfBoundsException if encoded IElementType can not be found in IElementType registry
   */
  @NotNull
  default IElementType unpackToken(int data) {
    return IElementType.find((short)(data & 0xffff));
  }

  /**
   * Unpacks state from segment data returned by
   *
   * @param data see {@link com.intellij.openapi.editor.ex.util.SegmentArrayWithData#getSegmentData(int)}
   * @return lexer state stored in data
   */
  default int unpackState(int data) {
    return data >> 16;
  }

  /**
   * @param data data to check
   * @return true if encoded state is restartable
   */
  default boolean containsRestartableState(int data) {
    int state = unpackState(data);
    return isRestartableState(state);
  }

  /**
   * This method specifies which states are restartable
   *
   * @param state lexer state to check
   * @return true if state is restartable and false otherwise
   */
  boolean isRestartableState(int state);

  /**
   * This method is an extended form of {@link com.intellij.lexer.Lexer#start(CharSequence, int, int, int)} which provides an Iterable
   * based on {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter#mySegments}.
   * Iteration start from current token and goes in reverse order. This iterable can be used for getting some additional data needed for context
   * dependent restart.
   *
   * @param buffer       character data for lexing
   * @param startOffset  offset to start lexing from
   * @param endOffset    offset to stop lexing at
   * @param initialState state to start lexing with
   * @param lookBehindTokenIterable   preceding token infos starting form current in reverse order.
   */
  void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState, Iterable<TokenInfo> lookBehindTokenIterable);
}