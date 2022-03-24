// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.fileTypes.impl;

import com.intellij.openapi.fileTypes.UserBinaryFileType;
import org.intellij.images.ImagesBundle;
import org.intellij.images.ImagesIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ImageFileType extends UserBinaryFileType {
  public static final ImageFileType INSTANCE = new ImageFileType();

  private ImageFileType() {
  }

  @NotNull
  @Override
  public String getName() {
    return "Image";
  }

  @NotNull
  @Override
  public String getDescription() {
    return ImagesBundle.message("filetype.images.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return ImagesBundle.message("filetype.images.display.name");
  }

  @Override
  public Icon getIcon() {
    return ImagesIcons.ImagesFileType;
  }
}
