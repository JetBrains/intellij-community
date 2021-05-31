// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;

public abstract class EmptyInspectionTreeLinkProvider {
  public static final ExtensionPointName<EmptyInspectionTreeLinkProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.emptyInspectionTreeLinkProvider");

  @NotNull
  public abstract @Nls String getText();

  @NotNull
  public abstract ActionListener getActionListener(SingleInspectionProfilePanel panel);
}
