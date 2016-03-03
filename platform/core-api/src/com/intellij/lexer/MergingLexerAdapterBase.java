/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

public abstract class MergingLexerAdapterBase extends DelegateLexer {
  private IElementType myTokenType;
  private int myState;
  private int myTokenStart;

  public MergingLexerAdapterBase(final Lexer original){
    super(original);
  }

  public abstract MergeFunction getMergeFunction();

  @Override
  public void start(@NotNull final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);
    myTokenType = null;
    myState = 0;
    myTokenStart = 0;
  }

  @Override
  public int getState(){
    if (myTokenType == null) locateToken();
    return myState;
  }

  @Override
  public IElementType getTokenType(){
    if (myTokenType == null) locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart(){
    if (myTokenType == null) locateToken();
    return myTokenStart;
  }

  @Override
  public int getTokenEnd(){
    if (myTokenType == null) locateToken();
    return super.getTokenStart();
  }

  @Override
  public void advance(){
    myTokenType = null;
    myState = 0;
    myTokenStart = 0;
  }

  private void locateToken(){
    if (myTokenType == null){
      Lexer orig = getDelegate();

      myTokenType = orig.getTokenType();
      myTokenStart = orig.getTokenStart();
      myState = orig.getState();
      if (myTokenType == null) return;
      orig.advance();
      myTokenType = getMergeFunction().merge(myTokenType, orig);
    }
  }

  public Lexer getOriginal() {
    return getDelegate();
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    MyLexerPosition pos = (MyLexerPosition)position;

    getDelegate().restore(pos.getOriginalPosition());
    myTokenType = pos.getType();
    myTokenStart = pos.getOffset();
    myState = pos.getOldState();
  }

  @NotNull
  @Override
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

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
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
