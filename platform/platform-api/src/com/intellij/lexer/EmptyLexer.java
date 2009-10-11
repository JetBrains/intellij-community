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

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;

public class EmptyLexer extends LexerBase {
  private CharSequence myBuffer;
  private int myStartOffset;
  private int myEndOffset;

  private static final IElementType EMPTY_TOKEN_TYPE = new IElementType("empty token", Language.ANY);

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getState() {
    return 0;
  }

  public IElementType getTokenType() {
    return (myStartOffset < myEndOffset ? EMPTY_TOKEN_TYPE : null);
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

  public LexerPosition getCurrentPosition() {
    return new LexerPositionImpl(0, getState());
  }

  public void restore(LexerPosition position) {}

  public int getBufferEnd() {
    return myEndOffset;
  }

}
