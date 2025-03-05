package com.intellij.database.csv;

import com.intellij.openapi.util.TextRange;

public class ValueRange extends TextRange {
  public ValueRange(int startOffset, int endOffset) {
    super(startOffset, endOffset);
  }

  public ValueRange(int startOffset, int endOffset, boolean checkForProperTextRange) {
    super(startOffset, endOffset, checkForProperTextRange);
  }

  public CharSequence value(CharSequence s) {
    return subSequence(s);
  }
}
