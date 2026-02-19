// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public abstract class MergingLexerAdapterBase extends DelegateLexer {
  private IElementType myTokenType;
  private int myState;
  private int myTokenStart;

  public MergingLexerAdapterBase(Lexer original) {
    super(original);
  }

  public abstract MergeFunction getMergeFunction();

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);
    myTokenType = null;
    myState = 0;
    myTokenStart = 0;
  }

  @Override
  public int getState() {
    if (myTokenType == null) locateToken();
    return myState;
  }

  @Override
  public IElementType getTokenType() {
    if (myTokenType == null) locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    if (myTokenType == null) locateToken();
    return myTokenStart;
  }

  @Override
  public int getTokenEnd() {
    if (myTokenType == null) locateToken();
    return super.getTokenStart();
  }

  @Override
  public void advance() {
    myTokenType = null;
    myState = 0;
    myTokenStart = 0;
  }

  private void locateToken() {
    if (myTokenType == null) {
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

  @Override
  public String toString() {
    return getClass().getName() + "[" + getDelegate() + "]";
  }

  @Override
  public @NotNull LexerPosition getCurrentPosition() {
    return new MyLexerPosition(myTokenStart, myTokenType, getDelegate().getCurrentPosition(), myState);
  }

  private static class MyLexerPosition implements LexerPosition {
    private final int myOffset;
    private final IElementType myTokenType;
    private final LexerPosition myOriginalPosition;
    private final int myOldState;

    MyLexerPosition(int offset, IElementType tokenType, LexerPosition originalPosition, int oldState) {
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
