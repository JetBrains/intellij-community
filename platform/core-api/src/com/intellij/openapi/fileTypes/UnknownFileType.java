// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class UnknownFileType implements FileType {
  public static final FileType INSTANCE = new UnknownFileType();

  private UnknownFileType() {}

  @Override
  @NotNull
  public String getName() {
    return "UNKNOWN";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Unknown";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Unknown;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return null;
  }
}
