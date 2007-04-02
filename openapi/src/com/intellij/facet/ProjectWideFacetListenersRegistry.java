/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ProjectWideFacetListenersRegistry {

  public static ProjectWideFacetListenersRegistry getInstance(Project project) {
    return ServiceManager.getService(project, ProjectWideFacetListenersRegistry.class);
  }

  public abstract <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener);
  public abstract <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener,
                                                          @NotNull Disposable parentDisposable);
  public abstract <F extends Facet> void unregisterListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener);

}
