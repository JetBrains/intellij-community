// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.ModificationTrackerListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class FacetModificationTrackingService {

  public static FacetModificationTrackingService getInstance(@NotNull Module module) {
    return module.getService(FacetModificationTrackingService.class);
  }

  public static FacetModificationTrackingService getInstance(@NotNull Facet<?> facet) {
    return facet.getModule().getService(FacetModificationTrackingService.class);
  }

  public abstract @NotNull ModificationTracker getFacetModificationTracker(@NotNull Facet<?> facet);

  public abstract void incFacetModificationTracker(@NotNull Facet<?> facet);

  public abstract <T extends Facet<?>> void addModificationTrackerListener(final T facet, final ModificationTrackerListener<? super T> listener, final Disposable parent);

  public abstract void removeModificationTrackerListener(final Facet<?> facet, final ModificationTrackerListener<?> listener);
}
