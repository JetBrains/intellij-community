/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

public class MergingLexerAdapterBase extends DelegateLexer {
  private IElementType myTokenType;
  private int myState;
  private int myTokenStart;
  private final MergeFunction myMergeFunction;

  public MergingLexerAdapterBase(final Lexer original, final MergeFunction mergeFunction){
    super(original);
    myMergeFunction = mergeFunction;
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);
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
    return super.getTokenStart();
  }

  public void advance(){
    myTokenType = null;
  }


  private void locateToken(){
    if (myTokenType == null){
      Lexer orig = getDelegate();

      myTokenType = orig.getTokenType();
      myTokenStart = orig.getTokenStart();
      myState = orig.getState();
      if (myTokenType == null) return;
      orig.advance();
      if (myMergeFunction.startMerge(myTokenType)){
        myTokenType = myMergeFunction.getMergedTokenType(myTokenType);
        while(true){
          IElementType tokenType = orig.getTokenType();
          if (!myMergeFunction.mergeWith(tokenType)) break;
          orig.advance();
        }
      }
    }
  }

  public Lexer getOriginal() {
    return getDelegate();
  }

  public void restore(LexerPosition position) {
    MyLexerPosition pos = (MyLexerPosition)position;

    getDelegate().restore(pos.getOriginalPosition());
    myTokenType = pos.getType();
    myTokenStart = pos.getOffset();
    myState = pos.getOldState();
  }

  public LexerPosition getCurrentPosition() {
    return new MyLexerPosition(myTokenStart, myTokenType, getDelegate().getCurrentPosition(), myState);
  }

  private static class MyLexerPosition implements LexerPosition{
    private final int myOffset;
    private final IElementType myTokenType;
    private final LexerPosition myOriginalPosition;
    private final int myOldState;

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

  protected interface MergeFunction {
    boolean startMerge(IElementType type);
    boolean mergeWith(IElementType type);
    IElementType getMergedTokenType(IElementType type);
  }
}
