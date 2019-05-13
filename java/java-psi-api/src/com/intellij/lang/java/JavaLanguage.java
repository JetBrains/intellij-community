// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaLanguage extends Language implements JvmLanguage {

  @NotNull
  public static final JavaLanguage INSTANCE = new JavaLanguage();

  private JavaLanguage() {
    super("JAVA", "text/x-java-source", "text/java", "application/x-java", "text/x-java");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Java";
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }
}
