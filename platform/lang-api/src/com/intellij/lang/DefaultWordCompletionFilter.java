/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

public class DefaultWordCompletionFilter implements WordCompletionElementFilter {
  public boolean isWordCompletionEnabledIn(final IElementType element) {
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
    return parserDefinition != null && parserDefinition.getCommentTokens().contains(element);
  }
}