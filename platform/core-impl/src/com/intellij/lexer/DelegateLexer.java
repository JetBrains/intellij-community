// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelegateLexer extends LexerBase {
  protected final Lexer myDelegate;

  public DelegateLexer(@NotNull Lexer delegate) {
    myDelegate = delegate;
  }

  public final Lexer getDelegate() {
    return myDelegate;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myDelegate.start(buffer, startOffset, endOffset, initialState);
  }

  @Override
  public int getState() {
    return myDelegate.getState();
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return myDelegate.getTokenType();
  }

  @Override
  public int getTokenStart() {
    return myDelegate.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    return myDelegate.getTokenEnd();
  }

  @Override
  public void advance() {
    myDelegate.advance();
  }

  @Override
  public final @NotNull CharSequence getBufferSequence() {
    return myDelegate.getBufferSequence();
  }

  @Override
  public int getBufferEnd() {
    return myDelegate.getBufferEnd();
  }
}
