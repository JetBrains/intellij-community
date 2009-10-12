/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.highlighter;

import com.intellij.lexer.JavaHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class JavaFileHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ourMap1;
  private static final Map<IElementType, TextAttributesKey> ourMap2;

  private final LanguageLevel myLanguageLevel;

  public JavaFileHighlighter() {
    this(LanguageLevel.HIGHEST);
  }

  public JavaFileHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  static {
    ourMap1 = new HashMap<IElementType, TextAttributesKey>();
    ourMap2 = new HashMap<IElementType, TextAttributesKey>();

    fillMap(ourMap1, JavaTokenType.KEYWORD_BIT_SET, SyntaxHighlighterColors.KEYWORD);
    fillMap(ourMap1, JavaTokenType.OPERATION_BIT_SET, SyntaxHighlighterColors.OPERATION_SIGN);
     fillMap(ourMap1, JavaTokenType.OPERATION_BIT_SET, SyntaxHighlighterColors.OPERATION_SIGN);

    for (IElementType type : JavaDocTokenType.ALL_JAVADOC_TOKENS.getTypes()) {
      ourMap1.put(type, SyntaxHighlighterColors.DOC_COMMENT);
    }

    ourMap1.put(XmlTokenType.XML_DATA_CHARACTERS, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap1.put(XmlTokenType.XML_REAL_WHITE_SPACE, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap1.put(XmlTokenType.TAG_WHITE_SPACE, SyntaxHighlighterColors.DOC_COMMENT);

    ourMap1.put(JavaTokenType.INTEGER_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.LONG_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.FLOAT_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.DOUBLE_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.STRING_LITERAL, SyntaxHighlighterColors.STRING);
    ourMap1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, SyntaxHighlighterColors.VALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, SyntaxHighlighterColors.INVALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, SyntaxHighlighterColors.INVALID_STRING_ESCAPE);
    ourMap1.put(JavaTokenType.CHARACTER_LITERAL, SyntaxHighlighterColors.STRING);

    ourMap1.put(JavaTokenType.LPARENTH, SyntaxHighlighterColors.PARENTHS);
    ourMap1.put(JavaTokenType.RPARENTH, SyntaxHighlighterColors.PARENTHS);

    ourMap1.put(JavaTokenType.LBRACE, SyntaxHighlighterColors.BRACES);
    ourMap1.put(JavaTokenType.RBRACE, SyntaxHighlighterColors.BRACES);

    ourMap1.put(JavaTokenType.LBRACKET, SyntaxHighlighterColors.BRACKETS);
    ourMap1.put(JavaTokenType.RBRACKET, SyntaxHighlighterColors.BRACKETS);

    ourMap1.put(JavaTokenType.COMMA, SyntaxHighlighterColors.COMMA);
    ourMap1.put(JavaTokenType.DOT, SyntaxHighlighterColors.DOT);
    ourMap1.put(JavaTokenType.SEMICOLON, SyntaxHighlighterColors.JAVA_SEMICOLON);

    //ourMap1[JavaTokenType.BOOLEAN_LITERAL] = HighlighterColors.JAVA_KEYWORD;
    //ourMap1[JavaTokenType.NULL_LITERAL] = HighlighterColors.JAVA_KEYWORD;
    ourMap1.put(JavaTokenType.C_STYLE_COMMENT, SyntaxHighlighterColors.JAVA_BLOCK_COMMENT);
    ourMap1.put(JavaDocElementType.DOC_COMMENT, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap1.put(JavaTokenType.END_OF_LINE_COMMENT, SyntaxHighlighterColors.LINE_COMMENT);
    ourMap1.put(JavaTokenType.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    ourMap1.put(JavaDocTokenType.DOC_TAG_NAME, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap2.put(JavaDocTokenType.DOC_TAG_NAME, SyntaxHighlighterColors.DOC_COMMENT_TAG);

    IElementType[] javaDocMarkup = new IElementType[]{XmlTokenType.XML_START_TAG_START,
                                        XmlTokenType.XML_END_TAG_START,
                                        XmlTokenType.XML_TAG_END,
                                        XmlTokenType.XML_EMPTY_ELEMENT_END,
                                        XmlTokenType.TAG_WHITE_SPACE,
                                        XmlTokenType.XML_TAG_NAME,
                                        XmlTokenType.XML_NAME,
                                        XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
                                        XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER,
                                        XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,
                                        XmlTokenType.XML_CHAR_ENTITY_REF,
                                        XmlTokenType.XML_EQ};

    for (IElementType idx : javaDocMarkup) {
      ourMap1.put(idx, SyntaxHighlighterColors.DOC_COMMENT);
      ourMap2.put(idx, SyntaxHighlighterColors.DOC_COMMENT_MARKUP);
    }
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new JavaHighlightingLexer(myLanguageLevel);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ourMap1.get(tokenType), ourMap2.get(tokenType));
  }
}