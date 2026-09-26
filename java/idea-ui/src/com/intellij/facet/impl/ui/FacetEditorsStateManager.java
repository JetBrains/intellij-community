// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui;

import com.intellij.facet.FacetType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FacetEditorsStateManager {

  public static FacetEditorsStateManager getInstance(@NotNull Project project) {
    return project.getService(FacetEditorsStateManager.class);
  }

  public abstract <T> void saveState(@NotNull FacetType<?, ?> type, @Nullable T state);

  public abstract @Nullable <T> T getState(@NotNull FacetType<?, ?> type, @NotNull Class<T> aClass);
}
