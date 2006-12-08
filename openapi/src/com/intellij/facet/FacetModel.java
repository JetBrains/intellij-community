/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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

}
