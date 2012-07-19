package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsJavaProjectExtension extends JpsElement {
  String getOutputUrl();

  void setOutputUrl(String outputUrl);

  LanguageLevel getLanguageLevel();

  void setLanguageLevel(LanguageLevel languageLevel);
}
