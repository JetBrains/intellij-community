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

/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

public class StoppableLexerAdapter implements Lexer {

  public static interface StoppingCondition {
    boolean stopsAt(IElementType token, int start, int end);
  }

  private Lexer myOriginal;
  private StoppingCondition myCondition;
  private boolean myStopped = false;

  public Lexer getOriginal() {
    return myOriginal;
  }

  public StoppableLexerAdapter(final StoppingCondition condition, final Lexer original) {
    myCondition = condition;
    myOriginal = original;
    myStopped = myCondition.stopsAt(myOriginal.getTokenType(), myOriginal.getTokenStart(), myOriginal.getTokenEnd());
  }

  public void advance() {
    if (myStopped) return;
    myOriginal.advance();

    if (myCondition.stopsAt(myOriginal.getTokenType(), myOriginal.getTokenStart(), myOriginal.getTokenEnd())) {
      myStopped = true;
    }
  }

  public int getPrevTokenEnd() {
    return myOriginal instanceof StoppableLexerAdapter ? ((StoppableLexerAdapter)myOriginal).getPrevTokenEnd() : ((FilterLexer)myOriginal).getPrevTokenEnd();
  }

  public char[] getBuffer() {
    return myOriginal.getBuffer();
  }

  public int getBufferEnd() {
    return myOriginal.getBufferEnd();
  }

  public LexerPosition getCurrentPosition() {
    return myOriginal.getCurrentPosition();
  }

  public int getState() {
    return myOriginal.getState();
  }

  public int getTokenEnd() {
    return myStopped ? myOriginal.getTokenStart() : myOriginal.getTokenEnd();
  }

  public int getTokenStart() {
    return myOriginal.getTokenStart();
  }

  public IElementType getTokenType() {
    return myStopped ? null : myOriginal.getTokenType();
  }

  public void restore(LexerPosition position) {
    myOriginal.restore(position);
  }

  public void start(char[] buffer) {
    myOriginal.start(buffer);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myOriginal.start(buffer, startOffset, endOffset);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
  }

  public CharSequence getBufferSequence() {
    return myOriginal.getBufferSequence();
  }
}