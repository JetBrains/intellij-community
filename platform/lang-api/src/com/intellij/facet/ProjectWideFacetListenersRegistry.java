// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a way to register listeners which will be notified about changes in facets in all modules.
 * Consider using {@link ProjectFacetListener} extension instead, it doesn't require calling code during project initialization.
 */
@ApiStatus.NonExtendable
public abstract class ProjectWideFacetListenersRegistry {
  public abstract <F extends Facet<?>> void registerListener(@NotNull FacetTypeId<F> typeId,
                                                             @NotNull ProjectWideFacetListener<? extends F> listener);

  public abstract <F extends Facet<?>> void registerListener(@NotNull FacetTypeId<F> typeId,
                                                             @NotNull ProjectWideFacetListener<? extends F> listener,
                                                             @NotNull Disposable parentDisposable);

  public abstract <F extends Facet<?>> void unregisterListener(@NotNull FacetTypeId<F> typeId,
                                                               @NotNull ProjectWideFacetListener<? extends F> listener);

  public abstract void registerListener(@NotNull ProjectWideFacetListener<Facet> listener);

  public abstract void unregisterListener(@NotNull ProjectWideFacetListener<Facet> listener);

  public abstract void registerListener(@NotNull ProjectWideFacetListener<Facet> listener, @NotNull Disposable parentDisposable);

  public static ProjectWideFacetListenersRegistry getInstance(@NotNull Project project) {
    return project.getService(ProjectWideFacetListenersRegistry.class);
  }
}
