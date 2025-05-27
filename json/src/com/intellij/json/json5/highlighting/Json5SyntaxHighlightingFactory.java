// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5.highlighting;

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.lexer.Lexer;
import com.intellij.json.json5.Json5Lexer;
import org.jetbrains.annotations.NotNull;

public final class Json5SyntaxHighlightingFactory extends JsonSyntaxHighlighterFactory {
  @Override
  protected @NotNull Lexer getLexer() {
    return new Json5Lexer();
  }

  @Override
  protected boolean isCanEscapeEol() {
    return true;
  }
}
