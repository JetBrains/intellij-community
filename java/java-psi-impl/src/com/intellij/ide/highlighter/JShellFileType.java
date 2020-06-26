// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.icons.AllIcons;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JShellFileType extends LanguageFileType {
  @NonNls public static final String DEFAULT_EXTENSION = "snippet";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;
  public static final JShellFileType INSTANCE = new JShellFileType();

  private JShellFileType() {
    super(JShellLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "JSHELL";
  }

  @Override
  @NotNull
  public String getDescription() {
    return JavaPsiBundle.message("filetype.description.jshell");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Java; // todo: a dedicated icon?
  }
}
