// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5.highlighting;

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.json.json5.Json5Language;
import com.intellij.json.syntax.json5.Json5SyntaxLexer;
import com.intellij.lexer.Lexer;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter;

public final class Json5SyntaxHighlightingFactory extends JsonSyntaxHighlighterFactory {
  @Override
  protected @NotNull Lexer getLexer() {
    return new LexerAdapter(new Json5SyntaxLexer(), getConverter(Json5Language.INSTANCE));
  }

  @Override
  protected boolean isCanEscapeEol() {
    return true;
  }
}
