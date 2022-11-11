// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.core.CoreBundle;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class UnknownFileType implements FileType {
  public static final FileType INSTANCE = new UnknownFileType();

  private UnknownFileType() { }

  @Override
  public @NotNull String getName() {
    return "UNKNOWN";
  }

  @Override
  public @NotNull String getDescription() {
    return CoreBundle.message("filetype.unknown.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return CoreBundle.message("filetype.unknown.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.UnknownFileType);
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
