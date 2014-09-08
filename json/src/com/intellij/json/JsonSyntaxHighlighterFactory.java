package com.intellij.json;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.json.JsonElementTypes.*;
import static com.intellij.psi.StringEscapesTokenTypes.*;

public class JsonSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return new MyHighlighter();
  }

  private static class MyHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> ourAttributes = new HashMap<IElementType, TextAttributesKey>();

    static {
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.BRACES, L_CURLY, R_CURLY);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.BRACKETS, L_BRACKET, R_BRACKET);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.COMMA, COMMA);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.SEMICOLON, COLON);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.STRING, DOUBLE_QUOTED_STRING);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.STRING, SINGLE_QUOTED_STRING);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.NUMBER, NUMBER);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.KEYWORD, TRUE, FALSE, NULL);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.LINE_COMMENT, LINE_COMMENT);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.BLOCK_COMMENT, BLOCK_COMMENT);
      // TODO may be it's worth to add more sensible highlighting for identifiers
      fillMap(ourAttributes, HighlighterColors.TEXT, INDENTIFIER);
      fillMap(ourAttributes, HighlighterColors.BAD_CHARACTER, TokenType.BAD_CHARACTER);

      // StringLexer's tokens
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, VALID_STRING_ESCAPE_TOKEN);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE, INVALID_CHARACTER_ESCAPE_TOKEN);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE, INVALID_UNICODE_ESCAPE_TOKEN);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      LayeredLexer layeredLexer = new LayeredLexer(new JsonLexer());
      layeredLexer.registerSelfStoppingLayer(new StringLiteralLexer('\"', DOUBLE_QUOTED_STRING, false, "/", false, false),
                                             new IElementType[]{DOUBLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
      layeredLexer.registerSelfStoppingLayer(new StringLiteralLexer('\'', SINGLE_QUOTED_STRING, false, "/", false, false),
                                             new IElementType[]{SINGLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
      return layeredLexer;
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType type) {
      return pack(ourAttributes.get(type));
    }
  }
}
