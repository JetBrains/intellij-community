// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JavaFileType extends LanguageFileType {
  public static final @NonNls String DEFAULT_EXTENSION = "java";
  public static final @NonNls String DOT_DEFAULT_EXTENSION = ".java";
  public static final JavaFileType INSTANCE = new JavaFileType();

  private JavaFileType() {
    super(JavaLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "JAVA";
  }

  @Override
  public @NotNull String getDescription() {
    return JavaPsiBundle.message("filetype.java.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.JavaFileType);
  }

  @Override
  public boolean isJVMDebuggingSupported() {
    return true;
  }
}
