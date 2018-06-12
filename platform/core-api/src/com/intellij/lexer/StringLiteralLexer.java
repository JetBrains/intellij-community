/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class StringLiteralLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.StringLiteralLexer");

  private static final short AFTER_FIRST_QUOTE = 1;
  private static final short AFTER_LAST_QUOTE = 2;

  public static final char NO_QUOTE_CHAR = (char)-1;

  protected CharSequence myBuffer;
  protected int myStart;
  protected int myEnd;
  private int myState;
  private int myLastState;
  protected int myBufferEnd;
  protected final char myQuoteChar;
  protected final IElementType myOriginalLiteralToken;
  private final boolean myCanEscapeEolOrFramingSpaces;
  private final String myAdditionalValidEscapes;
  private boolean mySeenEscapedSpacesOnly;
  private final boolean myAllowOctal;
  private final boolean myAllowHex;

  public StringLiteralLexer(char quoteChar, final IElementType originalLiteralToken) {
    this(quoteChar, originalLiteralToken, false, null);
  }

  /**
   * @param canEscapeEolOrFramingSpaces true if following sequences are acceptable
   *    '\' in the end of the buffer (meaning escaped end of line) or
   *    '\ ' (escaped space) in the beginning and in the end of the buffer (meaning escaped space, to avoid auto trimming on load)
   */
  public StringLiteralLexer(char quoteChar,
                            final IElementType originalLiteralToken,
                            boolean canEscapeEolOrFramingSpaces,
                            String additionalValidEscapes) {
    this(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces, additionalValidEscapes, true, false);
  }

  /**
   * @param canEscapeEolOrFramingSpaces true if following sequences are acceptable
   *    '\' in the end of the buffer (meaning escaped end of line) or
   */
  public StringLiteralLexer(char quoteChar,
                            final IElementType originalLiteralToken,
                            boolean canEscapeEolOrFramingSpaces,
                            String additionalValidEscapes,
                            boolean allowOctal,
                            boolean allowHex) {
    myQuoteChar = quoteChar;
    myOriginalLiteralToken = originalLiteralToken;
    myCanEscapeEolOrFramingSpaces = canEscapeEolOrFramingSpaces;
    myAdditionalValidEscapes = additionalValidEscapes;
    myAllowOctal = allowOctal;
    myAllowHex = allowHex;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStart = startOffset;
    myState = myQuoteChar == NO_QUOTE_CHAR ? AFTER_FIRST_QUOTE : initialState;
    myLastState = initialState;
    myBufferEnd = endOffset;
    myEnd = locateToken(myStart);
    mySeenEscapedSpacesOnly = true;
  }

  @Override
  public int getState() {
    return myLastState;
  }

  @Override
  public IElementType getTokenType() {
    if (myStart >= myEnd) return null;

    if (myBuffer.charAt(myStart) != '\\') {
      mySeenEscapedSpacesOnly = false;
      return myOriginalLiteralToken;
    }

    if (myStart + 1 >= myEnd) {
      return handleSingleSlashEscapeSequence();
    }
    char nextChar = myBuffer.charAt(myStart + 1);
    mySeenEscapedSpacesOnly &= nextChar == ' ';
    if (myCanEscapeEolOrFramingSpaces &&
        (nextChar == '\n' || nextChar == ' ' && (mySeenEscapedSpacesOnly || isTrailingSpace(myStart+2)))
      ) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }
    if (nextChar == 'u') {
      return getUnicodeEscapeSequenceType();
    }

    if (nextChar == 'x' && myAllowHex) {
      for(int i = myStart + 2; i < myStart + 4; i++) {
        if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    switch (nextChar) {
      case '0':
        if (shouldAllowSlashZero()) return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
        if (!myAllowOctal) return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
        //noinspection fallthrough
      case 'n':
      case 'r':
      case 'b':
      case 't':
      case 'f':
      case '\'':
      case '\"':
      case '\\':
        return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }
    if (myAdditionalValidEscapes != null && myAdditionalValidEscapes.indexOf(nextChar) != -1) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
  }

  protected boolean shouldAllowSlashZero() {
    return false;
  }

  @NotNull
  protected IElementType handleSingleSlashEscapeSequence() {
    return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
  }

  @NotNull
  protected IElementType getUnicodeEscapeSequenceType() {
    for (int i = myStart + 2; i < myStart + 6; i++) {
      if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
    }
    return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
  }

  // all subsequent chars are escaped spaces
  private boolean isTrailingSpace(final int start) {
    for (int i=start;i<myBufferEnd;i+=2) {
      final char c = myBuffer.charAt(i);
      if (c != '\\') return false;
      if (i==myBufferEnd-1) return false;
      if (myBuffer.charAt(i+1) != ' ') return false;
    }
    return true;
  }

  @Override
  public int getTokenStart() {
    return myStart;
  }

  @Override
  public int getTokenEnd() {
    return myEnd;
  }

  private int locateToken(int start) {
    if (start == myBufferEnd) {
      myState = AFTER_LAST_QUOTE;
    }
    if (myState == AFTER_LAST_QUOTE) return start;
    int i = start;
    if (myBuffer.charAt(i) == '\\') {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE, this);
      i++;
      if (i == myBufferEnd || myBuffer.charAt(i) == '\n' && !myCanEscapeEolOrFramingSpaces) {
        myState = AFTER_LAST_QUOTE;
        return i;
      }

      if (myAllowOctal && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
        char first = myBuffer.charAt(i);
        i++;
        if (i < myBufferEnd && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
          i++;
          if (i < myBufferEnd && first <= '3' && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
            i++;
          }
        }
        return i;
      }

      if (myAllowHex && myBuffer.charAt(i) == 'x') {
        return locateHexEscapeSequence(start, i);
      }

      if (myBuffer.charAt(i) == 'u') {
        return locateUnicodeEscapeSequence(start, i);
      }
      else {
        return i + 1;
      }
    }
    LOG.assertTrue(myState == AFTER_FIRST_QUOTE || myBuffer.charAt(i) == myQuoteChar, this);
    while (i < myBufferEnd) {
      if (myBuffer.charAt(i) == '\\') {
        return i;
      }
      if (myState == AFTER_FIRST_QUOTE && myBuffer.charAt(i) == myQuoteChar) {
        if (i + 1 == myBufferEnd) myState = AFTER_LAST_QUOTE;
        return i + 1;
      }
      i++;
      myState = AFTER_FIRST_QUOTE;
    }

    return i;
  }

  protected int locateHexEscapeSequence(int start, int i) {
    i++;
    for (; i < start + 4; i++) {
      if (i == myBufferEnd || myBuffer.charAt(i) == '\n' || myBuffer.charAt(i) == myQuoteChar) {
        return i;
      }
    }
    return i;
  }

  protected int locateUnicodeEscapeSequence(int start, int i) {
    i++;
    for (; i < start + 6; i++) {
      if (i == myBufferEnd || myBuffer.charAt(i) == '\n' || myBuffer.charAt(i) == myQuoteChar) {
        return i;
      }
    }
    return i;
  }

  @Override
  public void advance() {
    myLastState = myState;
    myStart = myEnd;
    myEnd = locateToken(myStart);
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String toString() {
    return "StringLiteralLexer {" +
           "myAllowHex=" + myAllowHex +
           ", myAllowOctal=" + myAllowOctal +
           ", mySeenEscapedSpacesOnly=" + mySeenEscapedSpacesOnly +
           ", myAdditionalValidEscapes='" + myAdditionalValidEscapes + '\'' +
           ", myCanEscapeEolOrFramingSpaces=" + myCanEscapeEolOrFramingSpaces +
           ", myOriginalLiteralToken=" + myOriginalLiteralToken +
           ", myQuoteChar=" + myQuoteChar +
           ", myBufferEnd=" + myBufferEnd +
           ", myLastState=" + myLastState +
           ", myState=" + myState +
           ", myEnd=" + myEnd +
           ", myStart=" + myStart +
           ", myToken=" + (myBuffer == null || myEnd<myStart || myEnd>myBuffer.length()? null : myBuffer.subSequence(myStart, myEnd)) +
           '}';
  }
}
