// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ArchiveFileType implements FileType {
  public static final ArchiveFileType INSTANCE = new ArchiveFileType();

  protected ArchiveFileType() {
  }

  @Override
  public @NotNull String getName() {
    return "ARCHIVE";
  }

  @Override
  public @NotNull String getDescription() {
    return CoreBundle.message("filetype.archive.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return CoreBundle.message("filetype.archive.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.ArchiveFileType);
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
