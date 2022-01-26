// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class IdeRootPaneNorthExtension {
  public static final ProjectExtensionPointName<IdeRootPaneNorthExtension> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.ideRootPaneNorth");

  @NotNull
  public abstract String getKey();

  @NotNull
  public abstract JComponent getComponent();

  public abstract void uiSettingsChanged(UISettings settings);

  public abstract IdeRootPaneNorthExtension copy();

  public void revalidate() {
  }
}
