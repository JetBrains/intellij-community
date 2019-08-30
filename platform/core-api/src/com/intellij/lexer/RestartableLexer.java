// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is suitable for providing even distribution for restartable states in highlighting lexer implementation.
 * Perfect highlighting lexer should be able to restart quickly from any context.
 * Sometimes it's not enough to have only one restartable state in lexer logic.
 * Implement {@link #isRestartableState(int)} to provide several restartable states.
 * <p/>
 * In some cases some additional information is needed to restart from non-trivial state.
 * {@link #start(CharSequence, int, int, int, TokenIterator)} implementation helps quickly retrieve information from preceding part of the file
 * to restore lexer properly.
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
   * Specifies which states are restartable.
   *
   * @param state lexer state to check
   * @return {@code true} if state is restartable, {@code false} otherwise.
   */
  @ApiStatus.Experimental
  boolean isRestartableState(int state);

  /**
   * Extended form of {@link Lexer#start(CharSequence, int, int, int)} which provides an Iterable
   * based on {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter#mySegments}.
   * Iteration starts from current token and goes in reverse order. This iterable can be used for getting some additional data needed for context
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