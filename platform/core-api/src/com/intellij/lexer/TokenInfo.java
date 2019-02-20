// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

import java.util.Objects;

public class TokenInfo {
  private final int myStartOffset;
  private final int myEndOffset;
  private final IElementType myType;
  private final int myState;

  public TokenInfo(int startOffset, int endOffset, IElementType type, int state) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myType = type;
    myState = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TokenInfo info = (TokenInfo)o;
    return myStartOffset == info.myStartOffset &&
           myEndOffset == info.myEndOffset &&
           myType.equals(info.myType) &&
           myState == info.myState;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myStartOffset, myEndOffset, myType, myState);
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public IElementType getType() {
    return myType;
  }

  public int getState() {
    return myState;
  }
}
