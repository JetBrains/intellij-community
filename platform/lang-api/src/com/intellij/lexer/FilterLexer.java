/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

public class FilterLexer extends DelegateLexer {
  private final Filter myFilter;
  private final boolean[] myStateFilter;
  private int myPrevTokenEnd;

  public interface Filter {
    boolean reject(IElementType type);
  }

  public static final class SetFilter implements Filter {
    private final TokenSet mySet;

    public SetFilter(TokenSet set) {
      mySet = set;
    }

    @Override
    public boolean reject(IElementType type) {
      return mySet.contains(type);
    }
  }

  public FilterLexer(Lexer original, Filter filter, boolean[] stateFilter) {
    super(original);
    myFilter = filter;
    myStateFilter = stateFilter;
  }

  public FilterLexer(Lexer original, Filter filter) {
    this(original, filter, null);
  }

  public Lexer getOriginal() {
    return getDelegate();
  }

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);
    myPrevTokenEnd = -1;
    locateToken();
  }


  @Override
  public void advance() {
    myPrevTokenEnd = getDelegate().getTokenEnd();
    super.advance();
    locateToken();
  }

  public int getPrevTokenEnd() {
    return myPrevTokenEnd;
  }

  @Override
  public LexerPosition getCurrentPosition() {
    return getDelegate().getCurrentPosition();    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void restore(LexerPosition position) {
    getDelegate().restore(position);
    myPrevTokenEnd = -1;
  }

  public final void locateToken(){
    while(true){
      Lexer delegate = getDelegate();
      IElementType tokenType = delegate.getTokenType();
      if (tokenType == null) break;
      if (myFilter == null || !myFilter.reject(tokenType)) {
        if (myStateFilter == null || !myStateFilter[delegate.getState()]){
          break;
        }
      }
      delegate.advance();
    }
  }
}

