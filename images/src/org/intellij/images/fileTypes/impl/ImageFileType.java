// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.fileTypes.impl;

import com.intellij.openapi.fileTypes.UserBinaryFileType;
import icons.ImagesIcons;
import org.intellij.images.ImagesBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ImageFileType extends UserBinaryFileType {
  public static final ImageFileType INSTANCE = new ImageFileType();

  @NotNull
  @Override
  public String getName() {
    return "Image";
  }

  @NotNull
  @Override
  public String getDescription() {
    return ImagesBundle.message("images.filetype.description");
  }

  @Override
  public Icon getIcon() {
    return ImagesIcons.ImagesFileType;
  }
}
