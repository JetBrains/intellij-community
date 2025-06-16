// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Internal
public final class FileStatusColorDescriptor {
  private final FileStatus myStatus;
  private Color myColor;
  private final Color myDefaultColor;
  private final Color myUiThemeColor;

  public FileStatusColorDescriptor(@NotNull FileStatus fileStatus,
                                   @Nullable Color color,
                                   @Nullable Color defaultColor,
                                   @Nullable Color uiThemeColor) {
    myStatus = fileStatus;
    myColor = color;
    myDefaultColor = defaultColor;
    myUiThemeColor = uiThemeColor;
  }

  public @NotNull FileStatus getStatus() {
    return myStatus;
  }

  public @Nullable Color getColor() {
    return myColor;
  }

  public void setColor(Color color) {
    myColor = color;
  }

  public boolean isDefault() {
    return Comparing.equal(myColor, myDefaultColor);
  }

  public void resetToDefault() {
    myColor = myDefaultColor;
  }

  public @Nullable Color getDefaultColor() {
    return myDefaultColor;
  }

  public @Nullable Color getUiThemeColor() {
    return myUiThemeColor;
  }
}
