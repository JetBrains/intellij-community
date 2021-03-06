// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.jsonpath.lexer.JsonPathLexer;
import com.intellij.jsonpath.psi.JsonPathTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

final class JsonPathSyntaxHighlighter extends SyntaxHighlighterBase {
  public static final TextAttributesKey JSONPATH_KEYWORD =
    createTextAttributesKey("JSONPATH.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey JSONPATH_IDENTIFIER =
    createTextAttributesKey("JSONPATH.IDENTIFIER", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

  public static final TextAttributesKey JSONPATH_CONTEXT =
    createTextAttributesKey("JSONPATH.CONTEXT", DefaultLanguageHighlighterColors.STATIC_FIELD);

  public static final TextAttributesKey JSONPATH_OPERATIONS =
    createTextAttributesKey("JSONPATH.OPERATIONS", DefaultLanguageHighlighterColors.OPERATION_SIGN);

  public static final TextAttributesKey JSONPATH_NUMBER =
    createTextAttributesKey("JSONPATH.NUMBER", DefaultLanguageHighlighterColors.NUMBER);

  public static final TextAttributesKey JSONPATH_BOOLEAN =
    createTextAttributesKey("JSONPATH.BOOLEAN", DefaultLanguageHighlighterColors.NUMBER);

  public static final TextAttributesKey JSONPATH_STRING =
    createTextAttributesKey("JSONPATH.STRING", DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey JSONPATH_PARENTHESES =
    createTextAttributesKey("JSONPATH.PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);

  public static final TextAttributesKey JSONPATH_BRACKETS =
    createTextAttributesKey("JSONPATH.BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);

  public static final TextAttributesKey JSONPATH_BRACES = // todo braces in object literals
    createTextAttributesKey("JSONPATH.BRACES", DefaultLanguageHighlighterColors.BRACES);

  public static final TextAttributesKey JSONPATH_COMMA =
    createTextAttributesKey("JSONPATH.COMMA", DefaultLanguageHighlighterColors.COMMA);

  public static final TextAttributesKey JSONPATH_DOT =
    createTextAttributesKey("JSONPATH.DOT", DefaultLanguageHighlighterColors.DOT);

  public static final TextAttributesKey JSONPATH_COLON =
    createTextAttributesKey("JSONPATH.COLON", DefaultLanguageHighlighterColors.COMMA);

  public static final TextAttributesKey JSONPATH_FUNCTION_CALL =
    createTextAttributesKey("JSONPATH.FUNCTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD);

  private static final Map<IElementType, TextAttributesKey> ourMap;

  static {
    ourMap = new HashMap<>();

    fillMap(ourMap, JSONPATH_KEYWORD,
            JsonPathTypes.WILDCARD, JsonPathTypes.FILTER_OPERATOR, JsonPathTypes.NULL, JsonPathTypes.NAMED_OP);
    fillMap(ourMap, JSONPATH_IDENTIFIER,
            JsonPathTypes.IDENTIFIER);
    fillMap(ourMap, JSONPATH_CONTEXT,
            JsonPathTypes.ROOT_CONTEXT, JsonPathTypes.EVAL_CONTEXT);
    fillMap(ourMap, JSONPATH_BRACKETS,
            JsonPathTypes.LBRACKET, JsonPathTypes.RBRACKET);
    fillMap(ourMap, JSONPATH_PARENTHESES,
            JsonPathTypes.LPARENTH, JsonPathTypes.RPARENTH);
    fillMap(ourMap, JSONPATH_DOT,
            JsonPathTypes.DOT, JsonPathTypes.RECURSIVE_DESCENT);
    fillMap(ourMap, JSONPATH_COMMA,
            JsonPathTypes.COMMA);
    fillMap(ourMap, JSONPATH_COLON,
            JsonPathTypes.COLON);

    fillMap(ourMap, JSONPATH_NUMBER,
            JsonPathTypes.INTEGER_NUMBER, JsonPathTypes.DOUBLE_NUMBER);
    fillMap(ourMap, JSONPATH_BOOLEAN,
            JsonPathTypes.TRUE, JsonPathTypes.FALSE);
    fillMap(ourMap, JSONPATH_STRING,
            JsonPathTypes.SINGLE_QUOTED_STRING, JsonPathTypes.DOUBLE_QUOTED_STRING, JsonPathTypes.REGEX_STRING);

    fillMap(ourMap, JSONPATH_OPERATIONS,
            JsonPathTypes.OR_OP, JsonPathTypes.AND_OP,
            JsonPathTypes.NOT_OP, JsonPathTypes.EQ_OP, JsonPathTypes.NE_OP, JsonPathTypes.RE_OP,
            JsonPathTypes.GT_OP, JsonPathTypes.LT_OP, JsonPathTypes.GE_OP, JsonPathTypes.LE_OP,
            JsonPathTypes.MINUS_OP, JsonPathTypes.PLUS_OP, JsonPathTypes.MULTIPLY_OP, JsonPathTypes.DIVIDE_OP);
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new JsonPathLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(ourMap.get(tokenType));
  }
}
