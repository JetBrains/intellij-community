package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

import java.io.IOException;
import java.io.Reader;

/**
 * @author max
 */
public interface FlexLexer {
  void yybegin(int state);
  int yystate();
  int getTokenStart();
  int getTokenEnd();
  IElementType advance() throws IOException;
  void yyreset(Reader reader);
}
