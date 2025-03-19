// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlaceInProjectStructure {
  public abstract @NotNull ProjectStructureElement getContainingElement();

  public abstract @Nullable String getPlacePath();

  public boolean canNavigate() {
    return true;
  }

  public abstract @NotNull ActionCallback navigate();
}
