// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

@ApiStatus.NonExtendable
public abstract class FacetFinder {

  public static FacetFinder getInstance(Project project) {
    return project.getService(FacetFinder.class);
  }

  public abstract @Nullable <F extends Facet<?> & FacetRootsProvider> F findFacet(VirtualFile file, FacetTypeId<F> type);

  public abstract @NotNull <F extends Facet<?> & FacetRootsProvider> @Unmodifiable Collection<F> findFacets(VirtualFile file, FacetTypeId<F> type);

  public abstract @NotNull <F extends Facet<?>> ModificationTracker getAllFacetsOfTypeModificationTracker(FacetTypeId<F> type);
}
