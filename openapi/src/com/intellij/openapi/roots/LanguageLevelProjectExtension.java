package com.intellij.openapi.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author Dmitry Avdeev
 */
public abstract class LanguageLevelProjectExtension {

  public static LanguageLevelProjectExtension getInstance(Project project) {
    return ServiceManager.getService(project, LanguageLevelProjectExtension.class);
  }

  public abstract LanguageLevel getLanguageLevel();

  public abstract void setLanguageLevel(LanguageLevel languageLevel);

  public abstract void reloadProjectOnLanguageLevelChange(LanguageLevel languageLevel, boolean forceReload);
}
