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
public class HexNumberParser extends PrefixedTokenParser {
  private HexNumberParser(String prefix) {
    super(prefix, CustomHighlighterTokenType.NUMBER);
  }

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
