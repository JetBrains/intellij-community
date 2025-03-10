// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public interface NavBarLeftSideExtension {
  ExtensionPointName<NavBarLeftSideExtension> EP_NAME = ExtensionPointName.create("com.intellij.navbarLeftSide");

  void process(@NotNull JComponent panel, @NotNull Project project);
}
