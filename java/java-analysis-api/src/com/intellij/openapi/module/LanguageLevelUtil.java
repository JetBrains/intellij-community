// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageLevelUtil {
  /**
   * Returns explicitly specified custom language level for {@code module}, or {@code null} if the module uses 'Project default' language level
   */
  public static @Nullable LanguageLevel getCustomLanguageLevel(@NotNull Module module) {
    LanguageLevelModuleExtension moduleExtension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
    return moduleExtension != null ? moduleExtension.getLanguageLevel() : null;
  }

  @NotNull
  public static LanguageLevel getEffectiveLanguageLevel(@NotNull final Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LanguageLevel level = getCustomLanguageLevel(module);
    if (level != null) return level;
    return LanguageLevelProjectExtension.getInstance(module.getProject()).getLanguageLevel();
  }
}
