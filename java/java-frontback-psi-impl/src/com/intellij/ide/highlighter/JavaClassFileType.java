// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JavaClassFileType implements FileType {
  public static final @NonNls String DEFAULT_EXTENSION = "class";
  public static final @NonNls String DOT_DEFAULT_EXTENSION = ".class";
  public static final JavaClassFileType INSTANCE = new JavaClassFileType();

  private JavaClassFileType() {
  }

  @Override
  public @NotNull String getName() {
    return "CLASS";
  }

  @Override
  public @NotNull String getDescription() {
    return JavaPsiBundle.message("filetype.class.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return JavaPsiBundle.message("filetype.class.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.JavaClassFileType);
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
