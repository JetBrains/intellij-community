package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

public abstract class CompositeLexer implements Lexer, Cloneable{
  private Lexer myLexer1;
  private Lexer myLexer2;
  private int myCurOffset;

  public CompositeLexer(Lexer lexer1, Lexer lexer2) {
    myLexer1 = lexer1;
    myLexer2 = lexer2;
  }

  public Object clone() {
    try{
      CompositeLexer clone = (CompositeLexer)super.clone();
      clone.myLexer1 = (Lexer)myLexer1.clone();
      clone.myLexer2 = (Lexer)myLexer2.clone();
      return clone;
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }

  protected abstract IElementType getCompositeTokenType(IElementType type1, IElementType type2);

  public void start(char[] buffer) {
    myLexer1.start(buffer);
    myLexer2.start(buffer);
    myCurOffset = 0;
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myLexer1.start(buffer, startOffset, endOffset);
    myLexer2.start(buffer, startOffset, endOffset);
    myCurOffset = startOffset;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myLexer1.start(buffer, startOffset, endOffset, initialState);
    myLexer2.start(buffer, startOffset, endOffset, initialState);
    myCurOffset = startOffset;
  }

  public int getState() {
    return 0; // does not work
  }

  public int getLastState() {
    return 0; // does not work
  }

  public IElementType getTokenType() {
    IElementType type1 = myLexer1.getTokenType();
    if (type1 == null) return null;
    IElementType type2 = myLexer2.getTokenType();
    return getCompositeTokenType(type1, type2);
  }

  public int getTokenStart() {
    return myCurOffset;
  }

  public int getTokenEnd() {
    return Math.min(myLexer1.getTokenEnd(), myLexer2.getTokenEnd());
  }

  public void advance() {
    int end1 = myLexer1.getTokenEnd();
    int end2 = myLexer2.getTokenEnd();
    myCurOffset = Math.min(end1, end2);
    if (myCurOffset == end1){
      myLexer1.advance();
    }
    if (myCurOffset == end2){
      myLexer2.advance();
    }
  }

  public char[] getBuffer() {
    return myLexer1.getBuffer();
  }

  public int getBufferEnd() {
    return myLexer1.getBufferEnd();
  }

  public int getSmartUpdateShift() {
    int shift1 = myLexer1.getSmartUpdateShift();
    if (shift1 < 0) return -1;
    int shift2 = myLexer2.getSmartUpdateShift();
    if (shift2 < 0) return -1;
    return Math.max(shift1, shift2);
  }
}