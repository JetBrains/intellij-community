package com.intellij.json;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;

public class JsonLanguage extends Language implements DependentLanguage /*To prevent HtmlScriptLanguageInjector */  {
  public static final JsonLanguage INSTANCE = new JsonLanguage();

  private JsonLanguage() {
    super("JSON", "application/json");
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }
}
