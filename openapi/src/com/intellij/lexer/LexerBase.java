package com.intellij.lexer;

/**
 *
 */
public abstract class LexerBase implements Lexer{
  public LexerPosition getCurrentPosition() {
    final int offset = getTokenStart();
    final int intState = getState();
    final LexerState state = new SimpleLexerState(intState);
    return new LexerPositionImpl(offset, state);
  }

  public void restore(LexerPosition position) {
    start(getBuffer(), position.getOffset(), getBufferEnd(), ((SimpleLexerState)position.getState()).getState());
  }
}
