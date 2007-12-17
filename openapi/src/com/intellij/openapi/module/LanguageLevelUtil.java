package com.intellij.openapi.module;

import org.jetbrains.annotations.NotNull;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.JavaPsiFacade;

/**
 * @author yole
 */
public class LanguageLevelUtil {
  private LanguageLevelUtil() {
  }

  @NotNull
  public static LanguageLevel getEffectiveLanguageLevel(final Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LanguageLevel level = ModuleRootManager.getInstance(module).getLanguageLevel();
    if (level != null) return level;
    return JavaPsiFacade.getInstance(module.getProject()).getEffectiveLanguageLevel();
  }
}
