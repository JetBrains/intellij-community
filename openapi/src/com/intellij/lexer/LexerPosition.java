package com.intellij.lexer;

public interface LexerPosition {
  int getOffset();
  LexerState getState();
}
