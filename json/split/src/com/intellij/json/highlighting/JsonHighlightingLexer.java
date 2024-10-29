// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.highlighting;

import com.intellij.json.JsonElementTypes;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;

public class JsonHighlightingLexer extends LayeredLexer {
  public JsonHighlightingLexer(boolean isPermissiveDialect, boolean canEscapeEol, Lexer baseLexer) {
    super(baseLexer);
    registerSelfStoppingLayer(new JsonStringLiteralLexer('\"', JsonElementTypes.DOUBLE_QUOTED_STRING, canEscapeEol, isPermissiveDialect),
                              new IElementType[]{JsonElementTypes.DOUBLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JsonStringLiteralLexer('\'', JsonElementTypes.SINGLE_QUOTED_STRING, canEscapeEol, isPermissiveDialect),
                                           new IElementType[]{JsonElementTypes.SINGLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
  }
}
