// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

interface ServiceViewUi {
  @NotNull JComponent getComponent();

  void saveState(@NotNull ServiceViewState state);

  void setServiceToolbar(@NotNull ServiceViewActionProvider actionManager);

  void setMasterComponent(@NotNull JComponent component, @NotNull ServiceViewActionProvider actionManager);

  void setDetailsComponentVisible(boolean visible);

  void setDetailsComponent(@Nullable JComponent component);

  void setNavBar(@NotNull JComponent component);

  @Nullable JComponent updateNavBar(boolean isSideComponent);

  void setMasterComponentVisible(boolean visible);

  @Nullable JComponent getDetailsComponent();

  void setSplitOrientation(boolean verticalSplit);
}
