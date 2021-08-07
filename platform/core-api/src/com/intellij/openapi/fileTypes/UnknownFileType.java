// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.core.CoreBundle;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nls;
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
    return CoreBundle.message("filetype.unknown.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return CoreBundle.message("filetype.unknown.display.name");
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
}
