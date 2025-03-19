// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JavaModuleFileType extends LanguageFileType {
  public static final JavaModuleFileType INSTANCE = new JavaModuleFileType();

  private JavaModuleFileType() {
    super(JavaLanguage.INSTANCE, true);
  }

  @Override
  public @NotNull String getName() {
    return "Java module";
  }

  @Override
  public @NotNull String getDescription() {
    return JavaPsiBundle.message("filetype.java.module.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return JavaFileType.DEFAULT_EXTENSION;
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return JavaPsiBundle.message("filetype.java.module.display.name");
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.JavaFileType);
  }
}
