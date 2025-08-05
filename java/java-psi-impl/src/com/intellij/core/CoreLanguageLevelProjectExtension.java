// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.JavaRelease;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class CoreLanguageLevelProjectExtension extends LanguageLevelProjectExtension {
  private LanguageLevel myLanguageLevel = JavaRelease.getHighest();
  private @Nullable Boolean myDefault;

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  @Override
  public @Nullable Boolean getDefault() {
    return myDefault;
  }

  @Override
  public void setDefault(@Nullable Boolean isDefault) {
    myDefault = isDefault;
  }
}
