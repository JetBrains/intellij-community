package com.intellij.lexer;

class LexerPositionImpl implements LexerPosition {
  private final int myOffset;
  private final LexerState myState;

  public LexerPositionImpl(final int offset, final LexerState state) {
    myOffset = offset;
    myState = state;
  }

  public int getOffset() {
    return myOffset;
  }

  public LexerState getState() {
    return myState;
  }
}
