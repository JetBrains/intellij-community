// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.ide.highlighter.custom.AbstractCustomLexer;
import com.intellij.ide.highlighter.custom.tokens.BraceTokenParser;
import com.intellij.ide.highlighter.custom.tokens.TokenParser;
import com.intellij.ide.highlighter.custom.tokens.WhitespaceParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class PlainTextSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @Override
  public @NotNull SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    return new SyntaxHighlighterBase() {
      @Override
      public @NotNull Lexer getHighlightingLexer() {
        return createPlainTextLexer();
      }

      @Override
      public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return TextAttributesKey.EMPTY_ARRAY;
      }
    };
  }

  public static @NotNull Lexer createPlainTextLexer() {
    ArrayList<TokenParser> tokenParsers = new ArrayList<>();
    tokenParsers.add(new WhitespaceParser());

    tokenParsers.addAll(BraceTokenParser.getBraces());
    tokenParsers.addAll(BraceTokenParser.getParens());
    tokenParsers.addAll(BraceTokenParser.getBrackets());
    tokenParsers.addAll(BraceTokenParser.getAngleBrackets());

    return new MergingLexerAdapter(new AbstractCustomLexer(tokenParsers), TokenSet.create(CustomHighlighterTokenType.CHARACTER));
  }
}