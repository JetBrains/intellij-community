// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class FacetModificationTrackingService {
  public static FacetModificationTrackingService getInstance(@NotNull Project project) {
    return project.getService(FacetModificationTrackingService.class);
  }

  public abstract @NotNull ModificationTracker getFacetModificationTracker(@NotNull Facet<?> facet);

  public abstract void incFacetModificationTracker(@NotNull Facet<?> facet);

  public abstract void incFacetModificationTracker();
}
