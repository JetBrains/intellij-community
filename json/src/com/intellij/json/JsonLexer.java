// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.syntax.JsonSyntaxLexer;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;

import static com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter;

/**
 * @author Mikhail Golubev
 */
public class JsonLexer extends LexerAdapter {
  public JsonLexer() {
    super(new JsonSyntaxLexer(), getConverter(JsonLanguage.INSTANCE));
  }
}
