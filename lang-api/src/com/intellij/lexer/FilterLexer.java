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

/**
 *
 */
public class FilterLexer extends LexerBase {
  private Lexer myOriginal;
  private Filter myFilter;
  private boolean[] myStateFilter;
  private int myPrevTokenEnd;

  public interface Filter {
    public boolean reject(IElementType type);
  }

  public static final class SetFilter implements Filter {
    private TokenSet mySet;

    public SetFilter(TokenSet set) {
      mySet = set;
    }

    public boolean reject(IElementType type) {
      return mySet.contains(type);
    }
  }

  public FilterLexer(Lexer original, Filter filter, boolean[] stateFilter) {
    myOriginal = original;
    myFilter = filter;
    myStateFilter = stateFilter;
  }

  public FilterLexer(Lexer original, Filter filter) {
    this(original, filter, null);
  }

  public Lexer getOriginal() {
    return myOriginal;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
    myPrevTokenEnd = -1;
    locateToken();
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
    myPrevTokenEnd = -1;
    locateToken();
  }

  public CharSequence getBufferSequence() {
    return myOriginal.getBufferSequence();
  }

  public int getState() {
    return myOriginal.getState();
  }

  public IElementType getTokenType() {
    return myOriginal.getTokenType();
  }

  public int getTokenStart() {
    return myOriginal.getTokenStart();
  }

  public int getTokenEnd() {
    return myOriginal.getTokenEnd();
  }

  public char[] getBuffer() {
    return myOriginal.getBuffer();
  }

  public int getBufferEnd() {
    return myOriginal.getBufferEnd();
  }

  public void advance() {
    myPrevTokenEnd = myOriginal.getTokenEnd();
    myOriginal.advance();
    locateToken();
  }

  public int getPrevTokenEnd() {
    return myPrevTokenEnd;
  }

  public LexerPosition getCurrentPosition() {
    return myOriginal.getCurrentPosition();    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void restore(LexerPosition position) {
    myOriginal.restore(position);
    myPrevTokenEnd = -1;
  }

  public final void locateToken(){
    while(true){
      IElementType tokenType = myOriginal.getTokenType();
      if (tokenType == null) break;
      if (myFilter == null || !myFilter.reject(tokenType)) {
        if (myStateFilter == null || !myStateFilter[myOriginal.getState()]){
          break;
        }
      }
      myOriginal.advance();
    }
  }
}

