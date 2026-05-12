// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LanguageLevelUtil {
  private LanguageLevelUtil() { }

  /**
   * @param module to get the language level for.
   * @return explicitly specified language level for a {@link Module}, or {@code null} if the module uses 'Project default' language level.
   * May return an {@linkplain LanguageLevel#isUnsupported() unsupported} language level.
   */
  public static @Nullable LanguageLevel getCustomLanguageLevel(@NotNull Module module) {
    LanguageLevelModuleExtension moduleExtension =
      ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
    return moduleExtension != null ? moduleExtension.getLanguageLevel() : null;
  }

  /**
   * @param module to get the language level for.
   * @return the effective language level for a {@link Module}, which is either the overridden language level for the module or the project
   * language level.
   * May return {@linkplain LanguageLevel#isUnsupported() unsupported} language level.
   */
  public static @NotNull LanguageLevel getEffectiveLanguageLevel(@NotNull Module module) {
    LanguageLevel level = getCustomLanguageLevel(module);
    if (level != null) return level;
    return LanguageLevelProjectExtension.getInstance(module.getProject()).getLanguageLevel();
  }
}
