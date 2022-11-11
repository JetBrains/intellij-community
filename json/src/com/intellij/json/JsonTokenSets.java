// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.psi.tree.TokenSet;

import static com.intellij.json.JsonElementTypes.*;
import static com.intellij.json.JsonElementTypes.LINE_COMMENT;

public class JsonTokenSets {
  public static final TokenSet STRING_LITERALS = TokenSet.create(SINGLE_QUOTED_STRING, DOUBLE_QUOTED_STRING);

  public static final TokenSet JSON_CONTAINERS = TokenSet.create(OBJECT, ARRAY);
  public static final TokenSet JSON_KEYWORDS = TokenSet.create(TRUE, FALSE, NULL);
  public static final TokenSet JSON_LITERALS = TokenSet.create(STRING_LITERAL, NUMBER_LITERAL, NULL_LITERAL, TRUE, FALSE);
  public static final TokenSet JSON_COMMENTARIES = TokenSet.create(BLOCK_COMMENT, LINE_COMMENT);
}