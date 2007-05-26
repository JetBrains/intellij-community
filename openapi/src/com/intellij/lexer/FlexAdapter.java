/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;

/**
 * @author max
 */
public class FlexAdapter extends LexerBase {
  private FlexLexer myFlex = null;
  private IElementType myTokenType = null;
  private CharSequence myText;

  private int myEnd;
  private int myState;

  public FlexAdapter(final FlexLexer flex) {
    myFlex = flex;
  }

  public FlexLexer getFlex() {
    return myFlex;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    final CharArrayCharSequence arrayCharSequence = new CharArrayCharSequence(buffer);
    start(arrayCharSequence, startOffset, endOffset, initialState);
  }

  public void start(final CharSequence buffer, int startOffset, int endOffset, final int initialState) {
    myText = buffer;
    myEnd = endOffset;
    try {
      myFlex.reset(myText, startOffset, endOffset, initialState);
    } catch(AbstractMethodError ame) {
      // Demetra compatibility
      myFlex.reset(myText.subSequence(startOffset, endOffset), initialState);
    }
    myTokenType = null;
  }

  public int getState() {
    locateToken();
    return myState;
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public int getTokenStart() {
    locateToken();
    return myFlex.getTokenStart();
  }

  public int getTokenEnd() {
    locateToken();
    return myFlex.getTokenEnd();
  }

  public void advance() {
    locateToken();
    myTokenType = null;
  }

  public char[] getBuffer() {
    return CharArrayUtil.fromSequence(myText);
  }

  public CharSequence getBufferSequence() {
    return myText;
  }

  public int getBufferEnd() {
    return myEnd;
  }

  private void locateToken() {
    if (myTokenType != null) return;
    try {
      myState = myFlex.yystate();
      myTokenType = myFlex.advance();
    }
    catch (IOException e) { /*Can't happen*/ }
  }
}
