/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public interface FacetModel {

  @NotNull
  Facet[] getSortedFacets();

  @NotNull
  Facet[] getAllFacets();

  @NotNull
  <F extends Facet> Collection<F> getFacetsByType(FacetTypeId<F> typeId);

  @Nullable
  <F extends Facet> F getFacetByType(FacetTypeId<F> typeId);

  @Nullable
  <F extends Facet> F findFacet(FacetTypeId<F> type, String name);

  @Nullable
  <F extends Facet> F getFacetByType(@NotNull Facet underlyingFacet, FacetTypeId<F> typeId);

  @NotNull
  <F extends Facet> Collection<F> getFacetsByType(@NotNull Facet underlyingFacet, FacetTypeId<F> typeId);

  void addListener(Listener listener, Disposable parent);
  
  interface Listener {
    void onChanged();
  }
}
