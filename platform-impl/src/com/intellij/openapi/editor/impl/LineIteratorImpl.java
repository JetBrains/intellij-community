package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.LineIterator;

/**
 *
 */
public class LineIteratorImpl implements LineIterator {
  private int myLineIndex = 0;
  private LineSet myLineSet;

  LineIteratorImpl(LineSet lineSet) {
    myLineSet = lineSet;
  }

  public void start(int startOffset) {
    myLineIndex = myLineSet.findLineIndex(startOffset);
  }

  public int getStart() {
    return myLineSet.getLineStart(myLineIndex);
  }

  public int getEnd() {
    return myLineSet.getLineEnd(myLineIndex);
  }

  public int getSeparatorLength() {
    return myLineSet.getSeparatorLength(myLineIndex);
  }

  public int getLineNumber() {
    return myLineIndex;
  }

  public void advance() {
    myLineIndex++;
  }

  public boolean atEnd() {
    return myLineIndex >= myLineSet.getLineCount() || myLineIndex < 0;
  }


}
