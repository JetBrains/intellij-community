// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class FileStatusColorDescriptor {
  private final FileStatus myStatus;
  private Color myColor;
  private final Color myDefaultColor;

  public FileStatusColorDescriptor(@NotNull FileStatus fileStatus, Color color, Color defaultColor) {
    myStatus = fileStatus;
    myColor = color;
    myDefaultColor = defaultColor;
  }

  public @NotNull FileStatus getStatus() {
    return myStatus;
  }

  public Color getColor() {
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

  public Color getDefaultColor() {
    return myDefaultColor;
  }
}
