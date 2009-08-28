package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public final class NumberParser extends BaseTokenParser {
  private final String mySuffices;
  private final boolean myIgnoreCase;

  public NumberParser(String suffices, boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
    if (!myIgnoreCase) {
      mySuffices = suffices;
    } else {
      mySuffices = suffices.toLowerCase().concat(suffices.toUpperCase());
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

  public int getSmartUpdateShift() {
    return 2; // if previous character was a dec point
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
