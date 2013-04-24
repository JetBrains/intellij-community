/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public abstract class CompositeLexer extends LexerBase {
  private final Lexer myLexer1;
  private final Lexer myLexer2;
  private int myCurOffset;

  public CompositeLexer(Lexer lexer1, Lexer lexer2) {
    myLexer1 = lexer1;
    myLexer2 = lexer2;
  }

  protected abstract IElementType getCompositeTokenType(IElementType type1, IElementType type2);

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myLexer1.start(buffer, startOffset, endOffset, (initialState >> 16) & 0xFFFF);
    myLexer2.start(buffer, startOffset, endOffset, initialState & 0xFFFF);
    myCurOffset = startOffset;
  }

  @Override
  public CharSequence getBufferSequence() {
    return myLexer1.getBufferSequence();
  }

  @Override
  public int getState() {
    final int state = myLexer1.getState();
    final int state2 = myLexer2.getState();

    if (state >= 0 && state < Short.MAX_VALUE &&
       state2 >= 0 && state2 < Short.MAX_VALUE) {
      return (state << 16) + state2;
    }

    return 0;
  }

  @Override
  public IElementType getTokenType() {
    IElementType type1 = myLexer1.getTokenType();
    if (type1 == null) return null;
    IElementType type2 = myLexer2.getTokenType();
    return getCompositeTokenType(type1, type2);
  }

  @Override
  public int getTokenStart() {
    return myCurOffset;
  }

  @Override
  public int getTokenEnd() {
    return Math.min(myLexer1.getTokenEnd(), myLexer2.getTokenEnd());
  }

  @Override
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

  @Override
  public int getBufferEnd() {
    return myLexer1.getBufferEnd();
  }

}
