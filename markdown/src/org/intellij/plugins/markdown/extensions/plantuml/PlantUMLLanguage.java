package org.intellij.plugins.markdown.extensions.plantuml;

import com.intellij.lang.Language;

public class PlantUMLLanguage extends Language {
  public static final PlantUMLLanguage INSTANCE = new PlantUMLLanguage();

  protected PlantUMLLanguage() {
    super("PlantUML");
  }
}