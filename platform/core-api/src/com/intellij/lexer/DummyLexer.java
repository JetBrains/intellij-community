// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class DummyLexer extends LexerBase {
  private CharSequence myBuffer;
  private int myStartOffset;
  private int myEndOffset;
  private final IElementType myTokenType;

  public DummyLexer(IElementType type) {
    myTokenType = type;
  }

  @Override
  public void start(final @NotNull CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public IElementType getTokenType() {
    return myStartOffset < myEndOffset ? myTokenType : null;
  }

  @Override
  public int getTokenStart() {
    return myStartOffset;
  }

  @Override
  public int getTokenEnd() {
    return myEndOffset;
  }

  @Override
  public void advance() {
    myStartOffset = myEndOffset;
  }

  @Override
  public @NotNull LexerPosition getCurrentPosition() {
    return new LexerPositionImpl(0, getState());
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
  }

  @Override
  public int getBufferEnd() {
    return myEndOffset;
  }
}
