// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class JShellLanguage extends Language {
  public static final JShellLanguage INSTANCE = new JShellLanguage();

  private JShellLanguage() {
    super(JavaLanguage.INSTANCE, "JShellLanguage");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "JShell Snippet";
  }
}
