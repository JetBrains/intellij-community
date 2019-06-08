package com.intellij.psi.templateLanguages;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
* @author peter
*/
public class TemplateDataHighlighterWrapper implements SyntaxHighlighter {
  private final SyntaxHighlighter myHighlighter;

  public TemplateDataHighlighterWrapper(SyntaxHighlighter highlighter) {
    myHighlighter = highlighter;
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return myHighlighter.getHighlightingLexer();
  }

  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(final IElementType tokenType) {
    if (tokenType == TokenType.BAD_CHARACTER) {
      return TextAttributesKey.EMPTY_ARRAY;
    }

    return myHighlighter.getTokenHighlights(tokenType);
  }
}
