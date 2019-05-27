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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is suitable for providing even distribution for restartable states in highlighting lexer implementation.
 * Perfect highlighting lexer should be able to restart quickly from any context.
 * Sometimes it's not enough to have only one restartable state in lexer logic.
 * Implement {@link #isRestartableState(int)} to provide several restartable states.
 * In some cases some additonal information is needed to restart from non-trivial state.
 * {@link #start(CharSequence, int, int, int, TokenIterator)} implementation helps quickly retrieve information from preceding part of the file
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
   * This method specifies which states are restartable
   *
   * @param state lexer state to check
   * @return true if state is restartable and false otherwise
   */
  @ApiStatus.Experimental
  boolean isRestartableState(int state);

  /**
   * This method is an extended form of {@link Lexer#start(CharSequence, int, int, int)} which provides an Iterable
   * based on {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter#mySegments}.
   * Iteration start from current token and goes in reverse order. This iterable can be used for getting some additional data needed for context
   * dependent restart.
   *
   * @param buffer        character data for lexing
   * @param startOffset   offset to start lexing from
   * @param endOffset     offset to stop lexing at
   * @param initialState  state to start lexing with
   * @param tokenIterator iterator for getting info from preceding tokens
   */
  @ApiStatus.Experimental
  void start(@NotNull CharSequence buffer,
             int startOffset,
             int endOffset,
             int initialState,
             TokenIterator tokenIterator);
}