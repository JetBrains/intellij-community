// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import org.jetbrains.annotations.NotNull;

public abstract class LexerBase extends Lexer {
  @Override
  public @NotNull LexerPosition getCurrentPosition() {
    final int offset = getTokenStart();
    final int intState = getState();
    return new LexerPositionImpl(offset, intState);
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    start(getBufferSequence(), position.getOffset(), getBufferEnd(), position.getState());
  }
}
