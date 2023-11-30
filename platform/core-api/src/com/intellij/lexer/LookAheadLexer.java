// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ImmutableUserMap;
import org.jetbrains.annotations.NotNull;

public abstract class LookAheadLexer extends LexerBase {
  private int myLastOffset;
  private int myLastState;

  private final Lexer myBaseLexer;
  private int myTokenStart;
  private final MutableRandomAccessQueue<IElementType> myTypeCache;
  private final MutableRandomAccessQueue<Integer> myEndOffsetCache;

  public LookAheadLexer(@NotNull Lexer baseLexer, int capacity) {
    myBaseLexer = baseLexer;
    myTypeCache = new MutableRandomAccessQueue<>(capacity);
    myEndOffsetCache = new MutableRandomAccessQueue<>(capacity);
  }

  public LookAheadLexer(@NotNull Lexer baseLexer) {
    this(baseLexer, 64);
  }


  protected void addToken(IElementType type) {
    addToken(myBaseLexer.getTokenEnd(), type);
  }

  protected void addToken(int endOffset, IElementType type) {
    myTypeCache.addLast(type);
    myEndOffsetCache.addLast(endOffset);
  }

  protected void lookAhead(@NotNull Lexer baseLexer) {
    advanceLexer(baseLexer);
  }

  @Override
  public void advance() {
    if (!myTypeCache.isEmpty()) {
      myTypeCache.pullFirst();
      myTokenStart = myEndOffsetCache.pullFirst();
    }
    if (myTypeCache.isEmpty()) {
      doLookAhead();
    }
  }

  private void doLookAhead() {
    myLastOffset = myTokenStart;
    myLastState = myBaseLexer.getState();

    lookAhead(myBaseLexer);
    assert !myTypeCache.isEmpty();
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myBaseLexer.getBufferSequence();
  }

  @Override
  public int getBufferEnd() {
    return myBaseLexer.getBufferEnd();
  }

  protected int getCacheSize() {
    return myTypeCache.size();
  }

  protected void resetCacheSize(int size) {
    while (myTypeCache.size() > size) {
      myTypeCache.removeLast();
      myEndOffsetCache.removeLast();
    }
  }

  public IElementType replaceCachedType(int index, IElementType token) {
    return myTypeCache.set(index, token);
  }

  protected final IElementType getCachedType(int index) {
    return myTypeCache.get(index);
  }

  protected final int getCachedOffset(int index) {
    return myEndOffsetCache.get(index);
  }

  @Override
  public int getState() {
    int offset = myTokenStart - myLastOffset;
    return myLastState | (offset << 16);
  }

  @Override
  public int getTokenEnd() {
    return myEndOffsetCache.peekFirst();
  }

  @Override
  public int getTokenStart() {
    return myTokenStart;
  }

  @Override
  public @NotNull LookAheadLexerPosition getCurrentPosition() {
    return new LookAheadLexerPosition(this, ImmutableUserMap.EMPTY);
  }

  @Override
  public final void restore(final @NotNull LexerPosition _position) {
    restore((LookAheadLexerPosition)_position);
  }

  protected void restore(@NotNull LookAheadLexerPosition position) {
    start(myBaseLexer.getBufferSequence(), position.lastOffset, myBaseLexer.getBufferEnd(), position.lastState);
    for (int i = 0; i < position.advanceCount; i++) {
      advance();
    }
  }

  @Override
  public IElementType getTokenType() {
    return myTypeCache.peekFirst();
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBaseLexer.start(buffer, startOffset, endOffset, initialState & 0xFFFF);
    myTokenStart = startOffset;
    myTypeCache.clear();
    myEndOffsetCache.clear();
    doLookAhead();
  }

  protected static class LookAheadLexerPosition implements LexerPosition {
    final int lastOffset;
    final int lastState;
    final int tokenStart;
    final int advanceCount;
    final ImmutableUserMap customMap;

    public LookAheadLexerPosition(@NotNull LookAheadLexer lookAheadLexer, @NotNull ImmutableUserMap map) {
      customMap = map;
      lastOffset = lookAheadLexer.myLastOffset;
      lastState = lookAheadLexer.myLastState;
      tokenStart = lookAheadLexer.myTokenStart;
      advanceCount = lookAheadLexer.myTypeCache.size() - 1;
    }

    public @NotNull ImmutableUserMap getCustomMap() {
      return customMap;
    }

    @Override
    public int getOffset() {
      return tokenStart;
    }

    @Override
    public int getState() {
      return lastState;
    }
  }

  protected final void advanceLexer(@NotNull Lexer lexer) {
    advanceAs(lexer, lexer.getTokenType());
  }

  protected final void advanceAs(@NotNull Lexer lexer, IElementType type) {
    addToken(type);
    lexer.advance();
  }
}
