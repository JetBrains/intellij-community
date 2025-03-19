// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;
import org.jetbrains.annotations.NotNull;

public final class JavaLanguage extends Language implements JvmLanguage {

  public static final @NotNull JavaLanguage INSTANCE = new JavaLanguage();

  private JavaLanguage() {
    super("JAVA", "text/x-java-source", "text/java", "application/x-java", "text/x-java");
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Java";
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }
}
