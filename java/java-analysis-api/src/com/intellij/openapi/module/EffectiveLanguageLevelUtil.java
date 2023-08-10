// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public final class EffectiveLanguageLevelUtil {
  /**
   * @deprecated use {@link LanguageLevelUtil#getEffectiveLanguageLevel(Module)} directly instead
   */
  @Deprecated
  @NotNull
  public static LanguageLevel getEffectiveLanguageLevel(@NotNull final Module module) {
    return LanguageLevelUtil.getEffectiveLanguageLevel(module);
  }
}
