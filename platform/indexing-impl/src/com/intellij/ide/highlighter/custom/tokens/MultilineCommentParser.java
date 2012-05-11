/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public class MultilineCommentParser extends PrefixedTokenParser {
  private final char[] myEndDelimiter;

  private MultilineCommentParser(String startDelimiter, String endDelimiter) {
    super(startDelimiter, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
    myEndDelimiter = endDelimiter.toCharArray();
  }

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
