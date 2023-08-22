// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public final class PlainTextLanguage extends Language {

  public static final PlainTextLanguage INSTANCE = new PlainTextLanguage();

  private PlainTextLanguage() {
    super("TEXT", "text/plain");
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Plain text";
  }
}
