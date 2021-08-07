// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.NonExtendable
public abstract class FacetFinder {

  public static FacetFinder getInstance(Project project) {
    return project.getService(FacetFinder.class);
  }

  @Nullable
  public abstract <F extends Facet<?> & FacetRootsProvider> F findFacet(VirtualFile file, FacetTypeId<F> type);

  @NotNull
  public abstract <F extends Facet<?> & FacetRootsProvider> Collection<F> findFacets(VirtualFile file, FacetTypeId<F> type);

  public abstract <F extends Facet<?>> ModificationTracker getAllFacetsOfTypeModificationTracker(FacetTypeId<F> type);
}
