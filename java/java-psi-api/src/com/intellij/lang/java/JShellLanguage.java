// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class JShellLanguage extends Language {
  public static final JShellLanguage INSTANCE = new JShellLanguage();

  private JShellLanguage() {
    super(JavaLanguage.INSTANCE, "JShellLanguage");
  }

  @Override
  public @NotNull String getDisplayName() {
    return "JShell Snippet";
  }
}
