/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public interface FacetModel {
  /**
   * Returns all facets in the module sorted in such a way that a facet will occur before any of its subfacets
   * @return sorted array of facets
   */
  @NotNull
  Facet[] getSortedFacets();

  /**
   * @return all facets in the module
   */
  @NotNull
  Facet[] getAllFacets();

  /**
   * @param typeId type of facets
   * @return all facets of the given type
   */
  @NotNull
  <F extends Facet> Collection<F> getFacetsByType(FacetTypeId<F> typeId);

  /**
   * @param typeId type of facet
   * @return first facet of the given type or <code>null</code> if the module doesn't contain facets of this type
   */
  @Nullable
  <F extends Facet> F getFacetByType(FacetTypeId<F> typeId);

  /**
   * @param type type of facet
   * @param name name of facet
   * @return first facet of the given type with the given name or <code>null</code> if not found
   */
  @Nullable
  <F extends Facet> F findFacet(FacetTypeId<F> type, String name);

  /**
   * @param underlyingFacet facet
   * @param typeId type of subfacet
   * @return first subfacet of the given facet
   */
  @Nullable
  <F extends Facet> F getFacetByType(@NotNull Facet underlyingFacet, FacetTypeId<F> typeId);

  /**
   * @param underlyingFacet facet
   * @param typeId type of subfacet
   * @return all subfacets of the given facet
   */
  @NotNull
  <F extends Facet> Collection<F> getFacetsByType(@NotNull Facet underlyingFacet, FacetTypeId<F> typeId);

  @NotNull
  String getFacetName(@NotNull Facet facet);
}
