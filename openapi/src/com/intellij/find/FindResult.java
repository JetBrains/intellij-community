
package com.intellij.find;

import com.intellij.openapi.util.TextRange;

public abstract class FindResult extends TextRange {
  public FindResult(int startOffset, int endOffset) {
    super(startOffset, endOffset);
  }

  public abstract boolean isStringFound();
}



