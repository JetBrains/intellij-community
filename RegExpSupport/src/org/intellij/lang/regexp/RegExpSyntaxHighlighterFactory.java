package org.intellij.lang.regexp;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class RegExpSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  private ParserDefinition myParserDefinition;

  public RegExpSyntaxHighlighterFactory() {
    myParserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(RegExpLanguage.INSTANCE);
  }

  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new RegExpHighlighter(null, myParserDefinition);
  }
}
