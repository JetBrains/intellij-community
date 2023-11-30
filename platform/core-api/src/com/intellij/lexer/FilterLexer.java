// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

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
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
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
  public @NotNull LexerPosition getCurrentPosition() {
    return getDelegate().getCurrentPosition();
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
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

