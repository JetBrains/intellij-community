package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 2, 2005
 * Time: 11:58:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class BracePair {
  private IElementType myLeftBrace;
  private IElementType myRightBrace;
  private boolean myStructural;

  public BracePair(final IElementType leftBrace, final IElementType rightBrace, final boolean structural) {
    myLeftBrace = leftBrace;
    myRightBrace = rightBrace;
    myStructural = structural;
  }

  public IElementType getLeftBraceType() {
    return myLeftBrace;
  }

  public IElementType getRightBraceType() {
    return myRightBrace;
  }

  public boolean isStructural() {
    return myStructural;
  }
}
