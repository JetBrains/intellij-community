// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

@ApiStatus.NonExtendable
public interface FacetModel {
  /**
   * Returns all facets in the module sorted in such a way that a facet will occur before any of its subfacets
   * @return sorted array of facets
   */
  Facet<?> @NotNull [] getSortedFacets();

  /**
   * @return all facets in the module
   */
  Facet<?> @NotNull [] getAllFacets();

  /**
   * @param typeId type of facets
   * @return all facets of the given type
   */
  @NotNull @Unmodifiable
  <F extends Facet<?>> Collection<F> getFacetsByType(FacetTypeId<F> typeId);

  /**
   * @param typeId type of facet
   * @return first facet of the given type or {@code null} if the module doesn't contain facets of this type
   */
  @Nullable
  <F extends Facet<?>> F getFacetByType(FacetTypeId<F> typeId);

  /**
   * @param type type of facet
   * @param name name of facet
   * @return first facet of the given type with the given name or {@code null} if not found
   */
  @Nullable
  <F extends Facet<?>> F findFacet(FacetTypeId<F> type, String name);

  /**
   * @param underlyingFacet facet
   * @param typeId type of subfacet
   * @return first subfacet of the given facet
   */
  @Nullable
  <F extends Facet<?>> F getFacetByType(@NotNull Facet<?> underlyingFacet, FacetTypeId<F> typeId);

  /**
   * @param underlyingFacet facet
   * @param typeId type of subfacet
   * @return all subfacets of the given facet
   */
  @NotNull @Unmodifiable
  <F extends Facet<?>> Collection<F> getFacetsByType(@NotNull Facet<?> underlyingFacet, FacetTypeId<F> typeId);

  @NotNull
  @NlsSafe String getFacetName(@NotNull Facet<?> facet);
}
