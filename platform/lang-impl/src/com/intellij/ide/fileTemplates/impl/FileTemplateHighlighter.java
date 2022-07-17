// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class FileTemplateHighlighter extends SyntaxHighlighterBase {
  private final Lexer myLexer;

  public FileTemplateHighlighter() {
    myLexer = FileTemplateConfigurable.createDefaultLexer();
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return myLexer;
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    if (tokenType == FileTemplateTokenType.MACRO || tokenType == FileTemplateTokenType.DIRECTIVE) {
      return pack(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
    }

    return TextAttributesKey.EMPTY_ARRAY;
  }
}