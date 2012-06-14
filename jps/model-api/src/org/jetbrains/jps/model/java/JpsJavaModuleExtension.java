package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsUrlList;

/**
 * @author nik
 */
public interface JpsJavaModuleExtension extends JpsElement {
  JpsUrlList getJavadocRoots();

  JpsUrlList getAnnotationRoots();

  String getOutputUrl();

  void setOutputUrl(String outputUrl);

  String getTestOutputUrl();

  void setTestOutputUrl(String testOutputUrl);

  LanguageLevel getLanguageLevel();

  void setLanguageLevel(LanguageLevel languageLevel);

  boolean isInheritOutput();

  void setInheritOutput(boolean inheritOutput);

  boolean isExcludeOutput();

  void setExcludeOutput(boolean excludeOutput);
}
