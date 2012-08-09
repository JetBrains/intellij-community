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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public final class NumberParser extends TokenParser {
  private final String mySuffices;
  private final boolean myIgnoreCase;

  public NumberParser(String suffices, boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
    if (!myIgnoreCase) {
      mySuffices = suffices;
    } else {
      mySuffices = suffices.toLowerCase().concat(StringUtil.toUpperCase(suffices));
    }
  }

  public boolean hasToken(int position) {
    final int start = position;
    final char startChar = myBuffer.charAt(start);
    if(!isDigit(startChar)) return false;
    for (position++; position < myEndOffset; position++) {
      if (!isDigit(myBuffer.charAt(position))) break;
    }

    if (position < myEndOffset && myBuffer.charAt(position) == '.') {
      final int dotPosition = position;
      position++;

      if (position < myEndOffset && !isDigit(myBuffer.charAt(position))) {
        position = dotPosition;
      } else {
        // after decimal point
        for (; position < myEndOffset; position++) {
          if (!isDigit(myBuffer.charAt(position))) break;
        }
        if (position < myEndOffset) {
          final char finalChar = myBuffer.charAt(position);
          if (!isNumberTail(finalChar) && !isDelimiter(finalChar)) {
            position = dotPosition;
          }
        }
      }
    }
    while(position < myEndOffset && isNumberTail(myBuffer.charAt(position))) {
      position++;
    }

    myTokenInfo.updateData(start, position, CustomHighlighterTokenType.NUMBER);
    return true;
  }

  static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private boolean isDelimiter(char c) {
    return !Character.isLetter(c);
  }

  private boolean isSuffix(char c) {
    return mySuffices != null && mySuffices.indexOf(c) >= 0;
  }

  private boolean isNumberTail(char c) {
    return /*Character.isLetter(c) ||*/ isSuffix(c);
  }
}
