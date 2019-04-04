package com.intellij.psi.templateLanguages;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.TokenType.BAD_CHARACTER;

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
    if (tokenType == BAD_CHARACTER) {
      return new TextAttributesKey[0];
    }

    return myHighlighter.getTokenHighlights(tokenType);
  }
}
