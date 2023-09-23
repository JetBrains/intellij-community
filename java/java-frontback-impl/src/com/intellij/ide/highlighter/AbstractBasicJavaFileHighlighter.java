// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.psi.impl.source.BasicElementTypes.*;

public abstract class AbstractBasicJavaFileHighlighter extends SyntaxHighlighterBase {
  private final Map<IElementType, TextAttributesKey> ourMap1;
  private final Map<IElementType, TextAttributesKey> ourMap2;

  protected final LanguageLevel myLanguageLevel;

  public AbstractBasicJavaFileHighlighter(@NotNull LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    ourMap1 = new HashMap<>();
    ourMap2 = new HashMap<>();

    fillMap(ourMap1, BASIC_KEYWORD_BIT_SET, JavaHighlightingColors.KEYWORD);
    fillMap(ourMap1, BASIC_LITERAL_BIT_SET, JavaHighlightingColors.KEYWORD);
    fillMap(ourMap1, BASIC_OPERATION_BIT_SET, JavaHighlightingColors.OPERATION_SIGN);

    for (IElementType type : JavaDocTokenType.ALL_JAVADOC_TOKENS.getTypes()) {
      ourMap1.put(type, JavaHighlightingColors.DOC_COMMENT);
    }

    ourMap1.put(JavaTokenType.INTEGER_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.LONG_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.FLOAT_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.DOUBLE_LITERAL, JavaHighlightingColors.NUMBER);
    ourMap1.put(JavaTokenType.STRING_LITERAL, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.TEXT_BLOCK_LITERAL, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.STRING_TEMPLATE_BEGIN, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.STRING_TEMPLATE_MID, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.STRING_TEMPLATE_END, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.TEXT_BLOCK_TEMPLATE_MID, JavaHighlightingColors.STRING);
    ourMap1.put(JavaTokenType.TEXT_BLOCK_TEMPLATE_END, JavaHighlightingColors.STRING);

    ourMap1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, JavaHighlightingColors.VALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, JavaHighlightingColors.INVALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, JavaHighlightingColors.INVALID_STRING_ESCAPE);
    ourMap1.put(JavaTokenType.CHARACTER_LITERAL, JavaHighlightingColors.STRING);

    ourMap1.put(JavaTokenType.LPARENTH, JavaHighlightingColors.PARENTHESES);
    ourMap1.put(JavaTokenType.RPARENTH, JavaHighlightingColors.PARENTHESES);

    ourMap1.put(JavaTokenType.LBRACE, JavaHighlightingColors.BRACES);
    ourMap1.put(JavaTokenType.RBRACE, JavaHighlightingColors.BRACES);

    ourMap1.put(JavaTokenType.LBRACKET, JavaHighlightingColors.BRACKETS);
    ourMap1.put(JavaTokenType.RBRACKET, JavaHighlightingColors.BRACKETS);

    ourMap1.put(JavaTokenType.COMMA, JavaHighlightingColors.COMMA);
    ourMap1.put(JavaTokenType.DOT, JavaHighlightingColors.DOT);
    ourMap1.put(JavaTokenType.SEMICOLON, JavaHighlightingColors.JAVA_SEMICOLON);

    ourMap1.put(JavaTokenType.C_STYLE_COMMENT, JavaHighlightingColors.JAVA_BLOCK_COMMENT);
    ourMap1.put(DOC_COMMENT, JavaHighlightingColors.DOC_COMMENT);
    ourMap1.put(JavaTokenType.END_OF_LINE_COMMENT, JavaHighlightingColors.LINE_COMMENT);
    ourMap1.put(TokenType.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    ourMap1.put(JavaDocTokenType.DOC_TAG_NAME, JavaHighlightingColors.DOC_COMMENT);
    ourMap2.put(JavaDocTokenType.DOC_TAG_NAME, JavaHighlightingColors.DOC_COMMENT_TAG);
    //noinspection AbstractMethodCallInConstructor
    initAdditional(ourMap1, ourMap2);
  }

  protected abstract void initAdditional(@NotNull Map<IElementType, TextAttributesKey> map1,
                                         @NotNull Map<IElementType, TextAttributesKey> map2);

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    while (tokenType instanceof ParentProviderElementType parentProviderElementType) {
      if (parentProviderElementType.getParents().size() == 1) {
        tokenType = parentProviderElementType.getParents().iterator().next();
      }
    }
    return pack(ourMap1.get(tokenType), ourMap2.get(tokenType));
  }
}