/*
 * @author max
 */
package com.intellij.lexer;

public interface RestorableLexer {
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
}
