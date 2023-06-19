// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PlainSyntaxHighlighter implements SyntaxHighlighter {
  private static final TextAttributesKey[] ATTRS = {HighlighterColors.TEXT};

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new EmptyLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return ATTRS;
  }
}