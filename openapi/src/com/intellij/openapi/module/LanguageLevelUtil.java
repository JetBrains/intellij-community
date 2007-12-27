package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.JavaModuleExtension;
import com.intellij.openapi.roots.JavaProjectExtension;
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
    LanguageLevel level = JavaModuleExtension.getInstance(module).getLanguageLevel();
    if (level != null) return level;
    return JavaProjectExtension.getInstance(module.getProject()).getLanguageLevel();
  }
}
