// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public final class MultilineCommentParser extends PrefixedTokenParser {
  private final char[] myEndDelimiter;

  private MultilineCommentParser(String startDelimiter, String endDelimiter) {
    super(startDelimiter, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
    myEndDelimiter = endDelimiter.toCharArray();
  }

  @Override
  protected int getTokenEnd(int position) {
    for (; position < myEndOffset; position++) {
      // todo: implement KMP
      int pos = position;
      int i;
      for (i = 0; i < myEndDelimiter.length && pos < myEndOffset; i++, pos++) {
        if (myBuffer.charAt(pos) != myEndDelimiter[i]) break;
      }
      if (i == myEndDelimiter.length) return pos;
    }
    return position;
  }

  public static MultilineCommentParser create(String startDelimiter, String endDelimiter) {
    if(startDelimiter == null || endDelimiter == null) return null;
    final String trimmedStart = startDelimiter.trim();
    final String trimmedEnd = endDelimiter.trim();
    if (trimmedStart.length() > 0 && trimmedEnd.length() > 0) {
      return new MultilineCommentParser(trimmedStart, trimmedEnd);
    } else {
      return null;
    }
  }
}
