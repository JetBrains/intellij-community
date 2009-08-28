package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class InlineHandlers extends LanguageExtension<InlineHandler> {

  private final static InlineHandlers INSTANCE = new InlineHandlers();

  private InlineHandlers() {
    super("com.intellij.refactoring.inlineHandler");
  }

  public static List<InlineHandler> getInlineHandlers(Language language) {
    return INSTANCE.allForLanguage(language);
  }
}
