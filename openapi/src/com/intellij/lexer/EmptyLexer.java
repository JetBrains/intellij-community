package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

public class EmptyLexer implements Lexer, Cloneable{
  private char[] myBuffer;
  private int myStartOffset;
  private int myEndOffset;

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(buffer, startOffset, endOffset);
  }

  public int getState() {
    return 0;
  }

  public IElementType getTokenType() {
    return (myStartOffset < myEndOffset ? IElementType.find((short)0) : null);
  }

  public int getTokenStart() {
    return myStartOffset;
  }

  public int getTokenEnd() {
    return myEndOffset;
  }

  public void advance() {
    myStartOffset = myEndOffset;
  }

  public char[] getBuffer() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myEndOffset;
  }

}