// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.wm;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class IdeRootPaneNorthExtension implements Disposable {
  public static final ExtensionPointName<IdeRootPaneNorthExtension> EP_NAME = ExtensionPointName.create("com.intellij.ideRootPaneNorth");

  @NotNull
  public abstract String getKey();

  public abstract JComponent getComponent();

  public abstract void uiSettingsChanged(UISettings settings);

  public abstract IdeRootPaneNorthExtension copy();

  public void revalidate(){}
}
