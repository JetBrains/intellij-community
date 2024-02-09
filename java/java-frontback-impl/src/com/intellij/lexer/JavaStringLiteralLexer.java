// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class JavaStringLiteralLexer extends StringLiteralLexer {

  JavaStringLiteralLexer(char quoteChar, IElementType originalLiteralToken) {
    super(quoteChar, originalLiteralToken);
  }

  JavaStringLiteralLexer(char quoteChar,
                         IElementType originalLiteralToken,
                         boolean canEscapeEolOrFramingSpaces,
                         String additionalValidEscapes) {
    super(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces, additionalValidEscapes);
  }

  @Override
  public IElementType getTokenType() {
    IElementType tokenType = super.getTokenType();
    if (tokenType == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN && myBuffer.length() > myStart + 1) {
      char c = myBuffer.charAt(myStart + 1);
      if (c == '{' && BasicElementTypes.BASIC_STRING_TEMPLATE_FRAGMENTS.contains(myOriginalLiteralToken)) {
        // don't highlight \{ in template fragment as bad escape
        return myOriginalLiteralToken;
      }
    }
    return tokenType;
  }

  @Override
  protected @NotNull IElementType getUnicodeEscapeSequenceType() {
    int start = myStart + 2;
    while (start < myEnd && myBuffer.charAt(start) == 'u') start++;
    if (start + 3 >= myEnd) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
    if (!StringUtil.isHexDigit(myBuffer.charAt(start)) ||
        !StringUtil.isHexDigit(myBuffer.charAt(start + 1)) ||
        !StringUtil.isHexDigit(myBuffer.charAt(start + 2)) ||
        !StringUtil.isHexDigit(myBuffer.charAt(start + 3))) {
      return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
    }
    return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
  }

  @Override
  protected int locateUnicodeEscapeSequence(int start, int i) {
    do {
      i++;
    }
    while (i < myBufferEnd && myBuffer.charAt(i) == 'u');
    int end = parseUnicodeDigits(i);
    if (end != i + 4) return end;
    int code = Integer.parseInt(myBuffer.subSequence(i, end).toString(), 16);
    i = end;
    // if escape sequence is not translated to backspace then continue from the next symbol
    if (code != '\\' || i >= myBufferEnd) return i;
    char c = myBuffer.charAt(i);
    if (StringUtil.isOctalDigit(c)) {
      if (i + 2 < myBufferEnd && StringUtil.isOctalDigit(myBuffer.charAt(i + 1)) && StringUtil.isOctalDigit(myBuffer.charAt(i + 2))) {
        return i + 3;
      }
    }
    else if (c == '\\' && i + 1 < myBufferEnd && myBuffer.charAt(i + 1) == 'u') {
      i += 2;
      while (i < myBufferEnd && myBuffer.charAt(i) == 'u') i++;
      return parseUnicodeDigits(i);
    }
    return i + 1;
  }

  private int parseUnicodeDigits(int i) {
    int end = i + 4;
    for (; i < end; i++) {
      if (i == myBufferEnd) return i;
      if (!StringUtil.isHexDigit(myBuffer.charAt(i))) return i;
    }
    return end;
  }
}
