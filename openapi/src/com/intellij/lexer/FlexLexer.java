package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

import java.io.IOException;

/**
 * @author max
 */
public interface FlexLexer {
  void yybegin(int state);
  int yystate();
  int getTokenStart();
  int getTokenEnd();
  IElementType advance() throws IOException;
  void reset(CharSequence buf, int initialState);
}
