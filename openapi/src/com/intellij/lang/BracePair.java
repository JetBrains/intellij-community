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
  private char myLeftBraceChar;
  private IElementType myRightBrace;
  private char myRightBraceChar;
  private boolean myStructural;

  public BracePair(char leftBraceChar, final IElementType leftBrace,char rightBraceChar,final IElementType rightBrace, final boolean structural) {
    myLeftBraceChar = leftBraceChar;
    myLeftBrace = leftBrace;
    myRightBraceChar = rightBraceChar;
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

  public char getLeftBraceChar() {
    return myLeftBraceChar;
  }

  public char getRightBraceChar() {
    return myRightBraceChar;
  }
}
