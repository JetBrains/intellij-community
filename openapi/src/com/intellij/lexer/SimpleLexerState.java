package com.intellij.lexer;

public class SimpleLexerState implements LexerState {
  private final int myIntState;

  public SimpleLexerState(final int intState) {
    myIntState = intState;
  }

  public int getState(){
    return myIntState;
  }

  public short intern() {
    return (short)myIntState;
  }
}
