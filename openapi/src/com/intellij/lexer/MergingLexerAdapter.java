package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class MergingLexerAdapter extends LexerBase {
  protected Lexer myOriginal;
  private TokenSet myTokensToMerge;
  protected IElementType myTokenType;
  protected int myState;
  protected int myTokenStart;

  public MergingLexerAdapter(Lexer original, TokenSet tokensToMerge){
    myOriginal = original;
    myTokensToMerge = tokensToMerge;
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

  protected void locateToken(){
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

  public Lexer getOriginal() {
    return myOriginal;
  }

  public void restore(LexerPosition position) {
    myOriginal.restore(position);
    myTokenType = null;
  }

  public LexerPosition getCurrentPosition() {
    return myOriginal.getCurrentPosition();
  }
}