// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.syntax.JsonSyntaxElementTypes;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.psi.tree.TokenSet;

import static com.intellij.json.JsonElementTypes.ARRAY;
import static com.intellij.json.JsonElementTypes.BLOCK_COMMENT;
import static com.intellij.json.JsonElementTypes.DOUBLE_QUOTED_STRING;
import static com.intellij.json.JsonElementTypes.FALSE;
import static com.intellij.json.JsonElementTypes.LINE_COMMENT;
import static com.intellij.json.JsonElementTypes.NULL_LITERAL;
import static com.intellij.json.JsonElementTypes.NUMBER_LITERAL;
import static com.intellij.json.JsonElementTypes.OBJECT;
import static com.intellij.json.JsonElementTypes.SINGLE_QUOTED_STRING;
import static com.intellij.json.JsonElementTypes.STRING_LITERAL;
import static com.intellij.json.JsonElementTypes.TRUE;
import static com.intellij.platform.syntax.SyntaxElementTypeSetKt.syntaxElementTypeSetOf;

public final class JsonTokenSets {
  public static final TokenSet STRING_LITERALS = TokenSet.create(SINGLE_QUOTED_STRING, DOUBLE_QUOTED_STRING);

  public static final TokenSet JSON_CONTAINERS = TokenSet.create(OBJECT, ARRAY);
  public static final SyntaxElementTypeSet JSON_KEYWORDS = syntaxElementTypeSetOf(JsonSyntaxElementTypes.INSTANCE.getTRUE(), JsonSyntaxElementTypes.INSTANCE.getFALSE(), JsonSyntaxElementTypes.INSTANCE.getNULL());
  public static final TokenSet JSON_LITERALS = TokenSet.create(STRING_LITERAL, NUMBER_LITERAL, NULL_LITERAL, TRUE, FALSE);
  public static final TokenSet JSON_COMMENTARIES = TokenSet.create(BLOCK_COMMENT, LINE_COMMENT);
}