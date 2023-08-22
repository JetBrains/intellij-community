// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JShellFileType extends LanguageFileType {
  public static final @NonNls String DEFAULT_EXTENSION = "snippet";
  public static final @NonNls String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;
  public static final JShellFileType INSTANCE = new JShellFileType();

  private JShellFileType() {
    super(JShellLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "JSHELL";
  }

  @Override
  public @NotNull String getDescription() {
    return JavaPsiBundle.message("filetype.jshell.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.JavaFileType); // todo: a dedicated icon?
  }
}
