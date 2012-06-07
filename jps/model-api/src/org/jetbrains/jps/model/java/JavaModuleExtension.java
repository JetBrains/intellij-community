package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JavaModuleExtension extends JpsElement {

  String getOutputUrl();

  void setOutputUrl(String outputUrl);

  String getTestOutputUrl();

  void setTestOutputUrl(String testOutputUrl);

  LanguageLevel getLanguageLevel();

  void setLanguageLevel(LanguageLevel languageLevel);
}
