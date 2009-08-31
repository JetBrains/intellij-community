package com.intellij.facet.impl.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.facet.FacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetEditorsStateManager {

  public static FacetEditorsStateManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FacetEditorsStateManager.class);
  }

  public abstract <T> void saveState(@NotNull FacetType<?, ?> type, @Nullable T state);

  @Nullable
  public abstract <T> T getState(@NotNull FacetType<?, ?> type, @NotNull Class<T> aClass);
}
