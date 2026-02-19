// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.json.syntax.json5.Json5SyntaxLexer;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;

import static com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter;

public final class Json5Lexer extends LexerAdapter {
  public Json5Lexer() {
    super(new Json5SyntaxLexer(), getConverter(Json5Language.INSTANCE));
  }
}
