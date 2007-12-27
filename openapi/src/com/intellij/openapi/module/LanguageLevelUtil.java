package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class LanguageLevelUtil {
  private LanguageLevelUtil() {
  }

  @NotNull
  public static LanguageLevel getEffectiveLanguageLevel(final Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LanguageLevel level = LanguageLevelModuleExtension.getInstance(module).getLanguageLevel();
    if (level != null) return level;
    return LanguageLevelProjectExtension.getInstance(module.getProject()).getLanguageLevel();
  }
}
