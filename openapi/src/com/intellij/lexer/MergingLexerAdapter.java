package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class MergingLexerAdapter implements Lexer, Cloneable{
  private Lexer myOriginal;
  private TokenSet myTokensToMerge;
  private IElementType myTokenType;
  private int myState;
  private int myTokenStart;

  public MergingLexerAdapter(Lexer original, TokenSet tokensToMerge){
    myOriginal = original;
    myTokensToMerge = tokensToMerge;
  }

  public Object clone() {
    try{
      MergingLexerAdapter clone = (MergingLexerAdapter)super.clone();
      clone.myOriginal = (Lexer)myOriginal.clone();
      return clone;
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }

  public void start(char[] buffer){
    myOriginal.start(buffer);
    myTokenType = null;
  }

  public void start(char[] buffer, int startOffset, int endOffset){
    myOriginal.start(buffer, startOffset, endOffset);
    myTokenType = null;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState){
    myOriginal.start(buffer, startOffset, endOffset, initialState);
    myTokenType = null;
  }

  public int getState(){
    locateToken();
    return myState;
  }

  public int getLastState() {
    return myOriginal.getLastState();
  }

  public IElementType getTokenType(){
    locateToken();
    return myTokenType;
  }

  public int getTokenStart(){
    locateToken();
    return myTokenStart;
  }

  public int getTokenEnd(){
    locateToken();
    return myOriginal.getTokenStart();
  }

  public void advance(){
    myTokenType = null;
  }

  public char[] getBuffer(){
    return myOriginal.getBuffer();
  }

  public int getBufferEnd(){
    return myOriginal.getBufferEnd();
  }

  private void locateToken(){                    
    if (myTokenType == null){
      myTokenType = myOriginal.getTokenType();
      myTokenStart = myOriginal.getTokenStart();
      myState = myOriginal.getState();
      if (myTokenType == null) return;
      myOriginal.advance();
      if (myTokensToMerge.isInSet(myTokenType)){
        while(true){
          IElementType tokenType = myOriginal.getTokenType();
          if (tokenType != myTokenType) break;
          myOriginal.advance();
        }
      }
    }
  }

  public int getSmartUpdateShift() {
    return myOriginal.getSmartUpdateShift();
  }
}