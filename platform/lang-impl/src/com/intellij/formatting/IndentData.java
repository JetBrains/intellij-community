package com.intellij.formatting;

import com.intellij.formatting.IndentInfo;

public class IndentData {
  private int myIndentSpaces = 0;
  private int mySpaces = 0;

  public IndentData(final int indentSpaces, final int spaces) {
    myIndentSpaces = indentSpaces;
    mySpaces = spaces;
  }

  public IndentData(final int indentSpaces) {
    this(indentSpaces, 0);
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  public void setIndentSpaces(final int indentSpaces) {
    myIndentSpaces = indentSpaces;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public void setSpaces(final int spaces) {
    mySpaces = spaces;
  }

  public IndentData add(final IndentData childOffset) {
    return new IndentData(myIndentSpaces + childOffset.getIndentSpaces(), mySpaces + childOffset.getSpaces());
  }

  public IndentData add(final WhiteSpace whiteSpace) {
    return new IndentData(myIndentSpaces + whiteSpace.getIndentOffset(), mySpaces + whiteSpace.getSpaces());
  }

  public boolean isEmpty() {
    return myIndentSpaces == 0 && mySpaces == 0;
  }

  public IndentInfo createIndentInfo() {
    return new IndentInfo(0, myIndentSpaces, mySpaces);
  }
}
