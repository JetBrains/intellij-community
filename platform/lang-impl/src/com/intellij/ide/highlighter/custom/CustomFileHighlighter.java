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

import java.util.HashMap;
import java.util.Map;

public class CustomFileHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ourKeys;
  private final SyntaxTable myTable;

  public CustomFileHighlighter(SyntaxTable table) {
    myTable = table;
  }

  static {
    ourKeys = new HashMap<>();

    ourKeys.put(CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterColors.CUSTOM_KEYWORD1_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.KEYWORD_2, CustomHighlighterColors.CUSTOM_KEYWORD2_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.KEYWORD_3, CustomHighlighterColors.CUSTOM_KEYWORD3_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.KEYWORD_4, CustomHighlighterColors.CUSTOM_KEYWORD4_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.NUMBER, CustomHighlighterColors.CUSTOM_NUMBER_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.STRING, CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.SINGLE_QUOTED_STRING, CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES);
    ourKeys.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, CustomHighlighterColors.CUSTOM_VALID_STRING_ESCAPE);
    ourKeys.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, CustomHighlighterColors.CUSTOM_INVALID_STRING_ESCAPE);
    ourKeys.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, CustomHighlighterColors.CUSTOM_INVALID_STRING_ESCAPE);
    ourKeys.put(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterColors.CUSTOM_LINE_COMMENT_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.MULTI_LINE_COMMENT,
                CustomHighlighterColors.CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES);
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
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(ourKeys.get(tokenType));
  }

  public static Map<IElementType, TextAttributesKey> getKeys() {
    return ourKeys;
  }
}
