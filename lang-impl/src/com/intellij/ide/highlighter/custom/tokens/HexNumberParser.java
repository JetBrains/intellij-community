package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public class HexNumberParser extends PrefixedTokenParser {
  private HexNumberParser(String prefix) {
    super(prefix, CustomHighlighterTokenType.NUMBER);
  }

  protected int getTokenEnd(int position) {
    for (; position < myEndOffset; position++) {
      if (!isHexDigit(myBuffer.charAt(position))) break;
    }
    return position;
  }

  public static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  public static HexNumberParser create(String prefix) {
    if (prefix == null) return null;
    final String trimmedPrefix = prefix.trim();
    if (trimmedPrefix.length() > 0) {
      return new HexNumberParser(prefix);
    } else {
      return null;
    }
  }
}
