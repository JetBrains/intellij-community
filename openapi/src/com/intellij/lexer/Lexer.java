package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 *
 */
public interface Lexer {
  void start(char[] buffer);
  void start(char[] buffer, int startOffset, int endOffset);
  void start(char[] buffer, int startOffset, int endOffset, int initialState);

  int getState();
  /**
   * @return number of states this lexer has.
   */
  int getLastState();

  IElementType getTokenType();

  int getTokenStart();
  int getTokenEnd();
  void advance();

  char[] getBuffer();
  int getBufferEnd();

  /**
   * @return number of characters to shift back from the change start to reparse
   */
  int getSmartUpdateShift();

  Object clone();
}
