package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.impl.util.TextDiffType;

import java.util.Comparator;

public class LineBlock {
  private final int myStartingLine1;
  private final int myModifiedLines1;
  private final int myStartingLine2;
  private final int myModifiedLines2;
  private final TextDiffType myType;

  public LineBlock(int startingLine1, int modifiedLines1, int startingLine2, int modifiedLines2, TextDiffType blockType) {
    myStartingLine1 = startingLine1;
    myModifiedLines1 = modifiedLines1;
    myStartingLine2 = startingLine2;
    myModifiedLines2 = modifiedLines2;
    myType = blockType;
  }

  public int getModifiedLines1() {
    return myModifiedLines1;
  }

  public int getStartingLine1() {
    return myStartingLine1;
  }

  public int getStartingLine2() {
    return myStartingLine2;
  }

  public int getModifiedLines2() {
    return myModifiedLines2;
  }

  protected int getEndLine1() {
    return myStartingLine1 + myModifiedLines1;
  }

  protected int getEndLine2() {
    return myStartingLine2 + myModifiedLines2;
  }

  public static final Comparator COMPARATOR = new Comparator<LineBlock>() {
    public int compare(LineBlock block1, LineBlock block2) {
      return block1.getStartingLine1() - block2.getStartingLine1();
    }
  };

  public TextDiffType getType() {
    return myType;
  }
}
