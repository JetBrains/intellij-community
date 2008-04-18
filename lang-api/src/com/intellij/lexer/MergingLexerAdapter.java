/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class MergingLexerAdapter extends LexerBase {
  private Lexer myOriginal;
  private TokenSet myTokensToMerge;
  private IElementType myTokenType;
  private int myState;
  private int myTokenStart;

  public MergingLexerAdapter(Lexer original, TokenSet tokensToMerge){
    myOriginal = original;
    myTokensToMerge = tokensToMerge;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState){
    myOriginal.start(buffer, startOffset, endOffset, initialState);
    myTokenType = null;
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
    myTokenType = null;
  }

  public CharSequence getBufferSequence() {
    return myOriginal.getBufferSequence();
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

  private void locateToken(){
    if (myTokenType == null){
      myTokenType = myOriginal.getTokenType();
      myTokenStart = myOriginal.getTokenStart();
      myState = myOriginal.getState();
      if (myTokenType == null) return;
      myOriginal.advance();
      if (myTokensToMerge.contains(myTokenType)){
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
    MyLexerPosition pos = (MyLexerPosition)position;

    myOriginal.restore(pos.getOriginalPosition());
    myTokenType = pos.getType();
    myTokenStart = pos.getOffset();
    myState = pos.getOldState();
  }

  public LexerPosition getCurrentPosition() {
    return new MyLexerPosition(myTokenStart, myTokenType, myOriginal.getCurrentPosition(), myState);
  }

  private static class MyLexerPosition implements LexerPosition{
    private int myOffset;
    private IElementType myTokenType;
    private LexerPosition myOriginalPosition;
    private int myOldState;

    public MyLexerPosition(final int offset, final IElementType tokenType, final LexerPosition originalPosition, int oldState) {
      myOffset = offset;
      myTokenType = tokenType;
      myOriginalPosition = originalPosition;
      myOldState = oldState;
    }

    public int getOffset() {
      return myOffset;
    }

    public int getState() {
      return myOriginalPosition.getState();
    }

    public IElementType getType() {
      return myTokenType;
    }

    public LexerPosition getOriginalPosition() {
      return myOriginalPosition;
    }

    public int getOldState() {
      return myOldState;
    }
  }
}