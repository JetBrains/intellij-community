// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class StringLiteralLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance(StringLiteralLexer.class);

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

  public StringLiteralLexer(char quoteChar, IElementType originalLiteralToken) {
    this(quoteChar, originalLiteralToken, false, null);
  }

  public StringLiteralLexer(char quoteChar, IElementType originalLiteralToken, boolean canEscapeEolOrFramingSpaces, String additionalValidEscapes) {
    this(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces, additionalValidEscapes, true, false);
  }

  /**
   * @param canEscapeEolOrFramingSpaces {@code true} if following sequences are acceptable:<br/>
   *                                    <b>*</b> {@code '\'} at the end of the buffer (meaning escaped end of line)<br/>
   *                                    <b>*</b> {@code '\ '} (escaped space) at the beginning and ath the end of the buffer
   *                                    (meaning escaped space, to avoid auto trimming on load)
   */
  public StringLiteralLexer(char quoteChar,
                            IElementType originalLiteralToken,
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
    if (myCanEscapeEolOrFramingSpaces && (nextChar == '\n' || nextChar == ' ' && (mySeenEscapedSpacesOnly || isTrailingSpace(myStart+2)))) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }
    if (nextChar == 'u') {
      return getUnicodeEscapeSequenceType();
    }

    if (nextChar == 'x' && myAllowHex) {
      return getHexCodedEscapeSeq();
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

  protected @NotNull IElementType handleSingleSlashEscapeSequence() {
    return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
  }

  protected IElementType getHexCodedEscapeSeq() {
    // \xFF
    return getStandardLimitedHexCodedEscapeSeq(4);
  }

  protected @NotNull IElementType getUnicodeEscapeSequenceType() {
    // \uFFFF
    return getStandardLimitedHexCodedEscapeSeq(6);
  }

  protected @NotNull IElementType getStandardLimitedHexCodedEscapeSeq(int offsetLimit) {
    for (int i = myStart + 2; i < myStart + offsetLimit; i++) {
      if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
    }
    return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
  }

  // all subsequent chars are escaped spaces
  private boolean isTrailingSpace(int start) {
    for (int i = start; i < myBufferEnd; i += 2) {
      char c = myBuffer.charAt(i);
      if (c != '\\') return false;
      if (i == myBufferEnd - 1) return false;
      if (myBuffer.charAt(i + 1) != ' ') return false;
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

      int additionalLocation = locateAdditionalEscapeSequence(start, i);
      if (additionalLocation != -1) {
        return additionalLocation;
      }
      return i + 1;
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

  /**
   * <p>Locates the end of an additional (non-standard) escape sequence. The sequence is considered to begin with a backslash symbol
   * located at the {@code start} index of the lexer's buffer.</p>
   *
   * <p>Override this method if your language supports non-standard escape sequences. For example, in Go language Unicode escapes look
   * like '\U12345678'. To locate this escape sequence, the implementation should check that {@code indexOfCharAfterSlash} points to the
   * 'U' symbol in the buffer and return {@code start + 8} (or the end index of the buffer if it is too short).
   * Otherwise, the implementations should return -1 to indicate that the current buffer starting at the index {@code start}
   * doesn't represent an additional escape sequence.</p>
   *
   * <p>When overriding this method, you most likely will need to also override {@link StringLiteralLexer#getTokenType}
   * to return proper type for the sequences located here.</p>
   */
  protected int locateAdditionalEscapeSequence(int start, int indexOfCharAfterSlash) {
    return -1;
  }

  @Override
  public void advance() {
    myLastState = myState;
    myStart = myEnd;
    myEnd = locateToken(myStart);
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

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
           ", myToken=" + (myBuffer == null || myEnd < myStart || myEnd > myBuffer.length() ? null : myBuffer.subSequence(myStart, myEnd)) +
           '}';
  }
}