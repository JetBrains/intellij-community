// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CustomHighlighterTokenType;

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
    if (!trimmedPrefix.isEmpty()) {
      return new HexNumberParser(prefix);
    } else {
      return null;
    }
  }
}
