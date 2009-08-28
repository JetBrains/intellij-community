package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class TokenInfo {
  private int myStart;
  private int myEnd;
  private IElementType myType;

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  public IElementType getType() {
    return myType;
  }

  public void updateData(int tokenStart, int tokenEnd, IElementType tokenType) {
    myStart = tokenStart;
    myEnd = tokenEnd;
    myType = tokenType;
  }

  public void updateData(TokenInfo info) {
    myStart = info.myStart;
    myEnd = info.myEnd;
    myType = info.myType;
  }
}
