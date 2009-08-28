/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

public class LanguageWordCompletion extends LanguageExtension<WordCompletionElementFilter> {
  public static final LanguageWordCompletion INSTANCE = new LanguageWordCompletion();

  private LanguageWordCompletion() {
    super("com.intellij.codeInsight.wordCompletionFilter", new DefaultWordCompletionFilter());
  }

  public boolean isEnabledIn(IElementType type) {
    return forLanguage(type.getLanguage()).isWordCompletionEnabledIn(type);
  }
}