// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class CoreLanguageLevelProjectExtension extends LanguageLevelProjectExtension {
  private LanguageLevel myLanguageLevel = LanguageLevel.HIGHEST;

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }
}
