// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface IdeRootPaneNorthExtension {
  ExtensionPointName<IdeRootPaneNorthExtension> EP_NAME = new ExtensionPointName<>("com.intellij.ideRootPaneNorth");

  @NotNull String getKey();

  @Nullable JComponent createComponent(@NotNull Project project, boolean isDocked);
}
