// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter.custom;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

public class CustomFileHighlighter extends SyntaxHighlighterBase {
  private static final @Unmodifiable @NotNull Map<@NotNull IElementType, @NotNull TextAttributesKey @NotNull []> ourKeys;
  private final SyntaxTable myTable;

  public CustomFileHighlighter(SyntaxTable table) {
    myTable = table;
  }

  static {
    ourKeys = Map.ofEntries(
    Map.entry(CustomHighlighterTokenType.KEYWORD_1, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_KEYWORD1_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.KEYWORD_2, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_KEYWORD2_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.KEYWORD_3, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_KEYWORD3_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.KEYWORD_4, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_KEYWORD4_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.NUMBER, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_NUMBER_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.STRING, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.SINGLE_QUOTED_STRING, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES}),
    Map.entry(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_VALID_STRING_ESCAPE}),
    Map.entry(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_INVALID_STRING_ESCAPE}),
    Map.entry(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_INVALID_STRING_ESCAPE}),
    Map.entry(CustomHighlighterTokenType.LINE_COMMENT, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_LINE_COMMENT_ATTRIBUTES}),
    Map.entry(CustomHighlighterTokenType.MULTI_LINE_COMMENT, new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES}));
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    Lexer customFileTypeLexer = new CustomFileTypeLexer(myTable, true);
    if (myTable.isHasStringEscapes()) {
      customFileTypeLexer = new LayeredLexer(customFileTypeLexer);
      ((LayeredLexer)customFileTypeLexer).registerSelfStoppingLayer(new StringLiteralLexer('\"', CustomHighlighterTokenType.STRING,true,"x"),
                                new IElementType[]{CustomHighlighterTokenType.STRING}, IElementType.EMPTY_ARRAY);
      ((LayeredLexer)customFileTypeLexer).registerSelfStoppingLayer(new StringLiteralLexer('\'', CustomHighlighterTokenType.STRING,true,"x"),
                                new IElementType[]{CustomHighlighterTokenType.SINGLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
    }
    return customFileTypeLexer;
  }

  @Override
  public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(@NotNull IElementType tokenType) {
    return ourKeys.getOrDefault(tokenType, TextAttributesKey.EMPTY_ARRAY);
  }
}
