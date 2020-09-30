// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @NotNull
  @Override
  public String getDisplayName() {
    return "Plain text";
  }
}
