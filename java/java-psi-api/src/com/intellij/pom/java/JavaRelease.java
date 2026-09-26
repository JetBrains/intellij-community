// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

public final class JavaRelease {
  private JavaRelease() { }

  /**
   * Returns the highest known stable language level of the Java programming language at the current moment in time.
   * This language level might be adjusted in the cloud registry, meaning the return value could change without a change in the distribution.
   * The reference the highest language level supported by the analyzer, please use {@link LanguageLevel#HIGHEST}.
   */
  public static @NotNull LanguageLevel getHighest() {
    LanguageLevel languageLevel = LanguageLevel.forFeature(Registry.intValue("java.highest.language.level"));
    if (languageLevel == null) throw new IllegalStateException("Highest language level could not be found");
    return languageLevel;
  }
}
