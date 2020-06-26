// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public final class HexNumberParser extends PrefixedTokenParser {
  private HexNumberParser(String prefix) {
    super(prefix, CustomHighlighterTokenType.NUMBER);
  }

  @Override
  protected int getTokenEnd(int position) {
    for (; position < myEndOffset; position++) {
      if (!StringUtil.isHexDigit(myBuffer.charAt(position))) break;
    }
    return position;
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
