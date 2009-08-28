package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.util.TextRange;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Nov 26, 2008
 * Time: 6:26:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class AffectedTextRange extends TextRange {
  private final boolean myProcessHeadingWhiteSpace;

  public AffectedTextRange(final int startOffset, final int endOffset, final boolean processHeadingWhiteSpace) {
    super(startOffset, endOffset);
    myProcessHeadingWhiteSpace = processHeadingWhiteSpace;
  }

  public AffectedTextRange(final TextRange affectedRange, final boolean processHeadingWhitespace) {
    this(affectedRange.getStartOffset(), affectedRange.getEndOffset(), processHeadingWhitespace);
  }

  public boolean isProcessHeadingWhiteSpace() {
    return myProcessHeadingWhiteSpace;
  }
}
