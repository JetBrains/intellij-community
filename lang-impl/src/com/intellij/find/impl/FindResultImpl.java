
package com.intellij.find.impl;

import com.intellij.find.FindResult;

public class FindResultImpl extends FindResult {
  private boolean isStringFound = true;

  public FindResultImpl(int startOffset, int endOffset) {
    super(startOffset, endOffset);
  }

  public FindResultImpl() {
    super(-1, -1);
    isStringFound = false;
  }

  public boolean isStringFound() {
    return isStringFound;
  }
}



