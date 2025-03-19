// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface UnscrambleSupport<T extends JComponent> {
  ExtensionPointName<UnscrambleSupport> EP_NAME = ExtensionPointName.create("com.intellij.unscrambleSupport");

  @Nullable
  String unscramble(@NotNull Project project, @NotNull String text, @NotNull String logName, @Nullable T settings);

  @NotNull
  @Contract(pure = true)
  String getPresentableName();

  default @Nullable T createSettingsComponent() {
    return null;
  }
}