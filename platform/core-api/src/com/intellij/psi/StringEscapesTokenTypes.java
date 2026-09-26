// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;


public interface StringEscapesTokenTypes {
  IElementType VALID_STRING_ESCAPE_TOKEN = new IElementType("VALID_STRING_ESCAPE_TOKEN", Language.ANY);
  IElementType INVALID_CHARACTER_ESCAPE_TOKEN = new IElementType("INVALID_CHARACTER_ESCAPE_TOKEN", Language.ANY);   // e.g. \x
  IElementType INVALID_UNICODE_ESCAPE_TOKEN = new IElementType("INVALID_UNICODE_ESCAPE_TOKEN", Language.ANY);       // e.g. \\u123z

  TokenSet STRING_LITERAL_ESCAPES = TokenSet.create(VALID_STRING_ESCAPE_TOKEN, INVALID_CHARACTER_ESCAPE_TOKEN, INVALID_UNICODE_ESCAPE_TOKEN);
}
