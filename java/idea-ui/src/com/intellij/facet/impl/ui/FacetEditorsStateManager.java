// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public abstract <T> T getState(@NotNull FacetType<?, ?> type, @NotNull Class<T> aClass);
}
