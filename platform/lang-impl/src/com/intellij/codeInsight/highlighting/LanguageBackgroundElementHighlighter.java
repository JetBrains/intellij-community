// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class LanguageBackgroundElementHighlighter extends LanguageExtension<BackgroundElementHighlighter> {
  public static final LanguageBackgroundElementHighlighter INSTANCE = new LanguageBackgroundElementHighlighter();

  private LanguageBackgroundElementHighlighter() {
    super("com.intellij.lang.backgroundElementHighlighter");
  }
}