// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.NonExtendable
public abstract class ProjectFacetManager {
  public static ProjectFacetManager getInstance(@NotNull Project project) {
    return project.getService(ProjectFacetManager.class);
  }

  public abstract boolean hasFacets(@NotNull FacetTypeId<?> typeId);

  public abstract <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId, final Module[] modules);

  @NotNull
  public abstract <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId);

  @NotNull
  public abstract List<Module> getModulesWithFacet(@NotNull FacetTypeId<?> typeId);

  public abstract <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull FacetType<?, C> facetType);

  public abstract <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull FacetType<?, C> facetType, @NotNull C configuration);
}
