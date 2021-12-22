// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class IdeRootPaneNorthExtension {
  public static final ExtensionPointName<IdeRootPaneNorthExtension> EP_NAME = new ExtensionPointName<>("com.intellij.ideRootPaneNorth");

  @NotNull
  public abstract String getKey();

  @NotNull
  public abstract JComponent getComponent();

  public abstract void uiSettingsChanged(UISettings settings);

  public abstract IdeRootPaneNorthExtension copy();

  public void revalidate() {
  }
}
