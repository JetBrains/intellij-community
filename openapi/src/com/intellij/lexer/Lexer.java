package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 *
 */
public interface Lexer {
  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset 0 and
   * terminated (EOFed) at offset buffer.length. Internal lexer state is supposed to be initial. YY_INITIAL for JLex and JFlex generated
   * lexer.
   * @param buffer character data for lexing.
   */
  void start(char[] buffer);

  /**
   * Prepare for lexing character data from <code>buffer</code> passed. Lexing should be performed starting from offset <code>startOffset> and
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
   * that value of initialState have been returned by {@link #getState()} method of this <code>Lexer</code> at condition <code>startOffset=getTokenStart()</code>
   * This method is used to incrementally relex changed characters using lexing data acquired from this particular lexer sometime in the past.
   *
   * @param buffer character data for lexing.
   * @param startOffset offset to start lexing from
   * @param endOffset offset to stop lexing at
   */
  void start(char[] buffer, int startOffset, int endOffset, int initialState);

  int getState();

  IElementType getTokenType();

  int getTokenStart();
  int getTokenEnd();
  void advance();

  char[] getBuffer();
  int getBufferEnd();
}
