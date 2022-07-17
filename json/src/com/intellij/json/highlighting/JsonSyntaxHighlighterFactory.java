// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.highlighting;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonLexer;
import com.intellij.lang.Language;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;

public class JsonSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  private static final String PERMISSIVE_ESCAPES;
  static {
    final StringBuilder escapesBuilder = new StringBuilder("/");
    for (char c = '\1'; c < '\255'; c++) {
      if (c != 'x' && c != 'u' && !Character.isDigit(c) && c != '\n' && c != '\r') {
        escapesBuilder.append(c);
      }
    }
    PERMISSIVE_ESCAPES = escapesBuilder.toString();
  }

  public static final TextAttributesKey JSON_BRACKETS = TextAttributesKey.createTextAttributesKey("JSON.BRACKETS", BRACKETS);
  public static final TextAttributesKey JSON_BRACES = TextAttributesKey.createTextAttributesKey("JSON.BRACES", BRACES);
  public static final TextAttributesKey JSON_COMMA = TextAttributesKey.createTextAttributesKey("JSON.COMMA", COMMA);
  public static final TextAttributesKey JSON_COLON = TextAttributesKey.createTextAttributesKey("JSON.COLON", SEMICOLON);
  public static final TextAttributesKey JSON_NUMBER = TextAttributesKey.createTextAttributesKey("JSON.NUMBER", NUMBER);
  public static final TextAttributesKey JSON_STRING = TextAttributesKey.createTextAttributesKey("JSON.STRING", STRING);
  public static final TextAttributesKey JSON_KEYWORD = TextAttributesKey.createTextAttributesKey("JSON.KEYWORD", KEYWORD);
  public static final TextAttributesKey JSON_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("JSON.LINE_COMMENT", LINE_COMMENT);
  public static final TextAttributesKey JSON_BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("JSON.BLOCK_COMMENT", BLOCK_COMMENT);

  // Artificial element type
  public static final TextAttributesKey JSON_IDENTIFIER = TextAttributesKey.createTextAttributesKey("JSON.IDENTIFIER", IDENTIFIER);

  // Added by annotators
  public static final TextAttributesKey JSON_PROPERTY_KEY = TextAttributesKey.createTextAttributesKey("JSON.PROPERTY_KEY", INSTANCE_FIELD);

  // String escapes
  public static final TextAttributesKey JSON_VALID_ESCAPE =
    TextAttributesKey.createTextAttributesKey("JSON.VALID_ESCAPE", VALID_STRING_ESCAPE);
  public static final TextAttributesKey JSON_INVALID_ESCAPE =
    TextAttributesKey.createTextAttributesKey("JSON.INVALID_ESCAPE", INVALID_STRING_ESCAPE);

  public static final TextAttributesKey JSON_PARAMETER = TextAttributesKey.createTextAttributesKey("JSON.PARAMETER", KEYWORD);


  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return new MyHighlighter(virtualFile);
  }

  private class MyHighlighter extends SyntaxHighlighterBase {
    private final Map<IElementType, TextAttributesKey> ourAttributes = new HashMap<>();

    @Nullable
    private final VirtualFile myFile;

    {
      fillMap(ourAttributes, JSON_BRACES, JsonElementTypes.L_CURLY, JsonElementTypes.R_CURLY);
      fillMap(ourAttributes, JSON_BRACKETS, JsonElementTypes.L_BRACKET, JsonElementTypes.R_BRACKET);
      fillMap(ourAttributes, JSON_COMMA, JsonElementTypes.COMMA);
      fillMap(ourAttributes, JSON_COLON, JsonElementTypes.COLON);
      fillMap(ourAttributes, JSON_STRING, JsonElementTypes.DOUBLE_QUOTED_STRING);
      fillMap(ourAttributes, JSON_STRING, JsonElementTypes.SINGLE_QUOTED_STRING);
      fillMap(ourAttributes, JSON_NUMBER, JsonElementTypes.NUMBER);
      fillMap(ourAttributes, JSON_KEYWORD, JsonElementTypes.TRUE, JsonElementTypes.FALSE, JsonElementTypes.NULL);
      fillMap(ourAttributes, JSON_LINE_COMMENT, JsonElementTypes.LINE_COMMENT);
      fillMap(ourAttributes, JSON_BLOCK_COMMENT, JsonElementTypes.BLOCK_COMMENT);
      // TODO may be it's worth to add more sensible highlighting for identifiers
      fillMap(ourAttributes, JSON_IDENTIFIER, JsonElementTypes.IDENTIFIER);
      fillMap(ourAttributes, HighlighterColors.BAD_CHARACTER, TokenType.BAD_CHARACTER);

      fillMap(ourAttributes, JSON_VALID_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
      fillMap(ourAttributes, JSON_INVALID_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
      fillMap(ourAttributes, JSON_INVALID_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
    }

    MyHighlighter(@Nullable VirtualFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      LayeredLexer layeredLexer = new LayeredLexer(getLexer());
      boolean isPermissiveDialect = isPermissiveDialect();
      layeredLexer.registerSelfStoppingLayer(new StringLiteralLexer('\"', JsonElementTypes.DOUBLE_QUOTED_STRING, isCanEscapeEol(),
                                                                    isPermissiveDialect ? PERMISSIVE_ESCAPES : "/", false, isPermissiveDialect) {
                                               @NotNull
                                               @Override
                                               protected IElementType handleSingleSlashEscapeSequence() {
                                                 return isPermissiveDialect ? myOriginalLiteralToken : super.handleSingleSlashEscapeSequence();
                                               }

                                               @Override
                                               protected boolean shouldAllowSlashZero() {
                                                 return isPermissiveDialect;
                                               }
                                             },
                                             new IElementType[]{JsonElementTypes.DOUBLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
      layeredLexer.registerSelfStoppingLayer(new StringLiteralLexer('\'', JsonElementTypes.SINGLE_QUOTED_STRING, isCanEscapeEol(),
                                                                    isPermissiveDialect ? PERMISSIVE_ESCAPES : "/", false, isPermissiveDialect){
                                               @NotNull
                                               @Override
                                               protected IElementType handleSingleSlashEscapeSequence() {
                                                 return isPermissiveDialect ? myOriginalLiteralToken : super.handleSingleSlashEscapeSequence();
                                               }

                                               @Override
                                               protected boolean shouldAllowSlashZero() {
                                                 return isPermissiveDialect;
                                               }
                                             },
                                             new IElementType[]{JsonElementTypes.SINGLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
      return layeredLexer;
    }

    private boolean isPermissiveDialect() {
      FileType fileType = myFile == null ? null : myFile.getFileType();
      boolean isPermissiveDialect = false;
      if (fileType instanceof JsonFileType) {
        Language language = ((JsonFileType)fileType).getLanguage();
        isPermissiveDialect = language instanceof JsonLanguage && ((JsonLanguage)language).hasPermissiveStrings();
      }
      return isPermissiveDialect;
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType type) {
      return pack(ourAttributes.get(type));
    }
  }

  @NotNull
  protected Lexer getLexer() {
    return new JsonLexer();
  }

  protected boolean isCanEscapeEol() {
    return false;
  }
}
