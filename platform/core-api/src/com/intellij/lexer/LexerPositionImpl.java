// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class LexerPositionImpl implements LexerPosition {
  private final int myOffset;
  private final int myState;

  public LexerPositionImpl(final int offset, final int state) {
    myOffset = offset;
    myState = state;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getState() {
    return myState;
  }
}
