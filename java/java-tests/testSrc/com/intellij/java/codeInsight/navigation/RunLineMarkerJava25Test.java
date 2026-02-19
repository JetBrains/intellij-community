// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class RunLineMarkerJava25Test extends RunLineMarkerJava23Test {

  @Override
  protected @NotNull LanguageLevel getEnabledLevel() {
    return LanguageLevel.JDK_25;
  }

  @Override
  protected @NotNull LanguageLevel getDisabledLevel() {
    return LanguageLevel.JDK_24;
  }
}
