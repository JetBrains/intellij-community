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

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class FacetModelBase implements FacetModel {
  private volatile Map<FacetTypeId, Collection<Facet>> myType2Facets;
  private volatile Map<Pair<Facet, FacetTypeId>, Collection<Facet>> myChildFacets;
  private volatile Facet[] mySortedFacets;

  @Override
  @NotNull
  public Facet[] getSortedFacets() {
    if (mySortedFacets == null) {
      final Facet[] allFacets = getAllFacets();
      if (allFacets.length == 0) {
        mySortedFacets = Facet.EMPTY_ARRAY;
      }
      else {
        LinkedHashSet<Facet> facets = new LinkedHashSet<Facet>();
        for (Facet facet : allFacets) {
          addUnderlyingFacets(facets, facet);
        }
        mySortedFacets = facets.toArray(new Facet[facets.size()]);
      }
    }
    return mySortedFacets;
  }

  private static void addUnderlyingFacets(final LinkedHashSet<Facet> facets, final Facet facet) {
    final Facet underlyingFacet = facet.getUnderlyingFacet();
    if (underlyingFacet != null && !facets.contains(facet)) {
      addUnderlyingFacets(facets, underlyingFacet);
    }
    facets.add(facet);
  }

  @Override
  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(@NotNull Facet underlyingFacet, FacetTypeId<F> typeId) {
    if (myChildFacets == null) {
      MultiValuesMap<Pair<Facet, FacetTypeId>, Facet> children = new MultiValuesMap<Pair<Facet, FacetTypeId>, Facet>();
      for (Facet facet : getAllFacets()) {
        final Facet underlying = facet.getUnderlyingFacet();
        if (underlying != null) {
          children.put(Pair.create(underlying,  facet.getTypeId()), facet);
        }
      }

      Map<Pair<Facet, FacetTypeId>, Collection<Facet>> childFacets = new HashMap<Pair<Facet, FacetTypeId>, Collection<Facet>>();
      for (Pair<Facet, FacetTypeId> pair : children.keySet()) {
        final Collection<Facet> facets = children.get(pair);
        childFacets.put(pair, Collections.unmodifiableCollection(facets));
      }
      myChildFacets = childFacets;
    }
    //noinspection unchecked
    final Collection<F> facets = (Collection<F>)myChildFacets.get(new Pair(underlyingFacet, typeId));
    return facets != null ? facets : Collections.<F>emptyList();
  }

  @Override
  @NotNull
  public String getFacetName(@NotNull Facet facet) {
    return facet.getName();
  }

  @Override
  @Nullable
  public <F extends Facet> F findFacet(final FacetTypeId<F> type, final String name) {
    final Collection<F> fs = getFacetsByType(type);
    for (F f : fs) {
      if (f.getName().equals(name)) {
        return f;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public <F extends Facet> F getFacetByType(@NotNull final Facet underlyingFacet, final FacetTypeId<F> typeId) {
    final Collection<F> fs = getFacetsByType(underlyingFacet, typeId);
    return fs.isEmpty() ? null : fs.iterator().next();
  }

  @Override
  @Nullable
  public <F extends Facet> F getFacetByType(FacetTypeId<F> typeId) {
    final Collection<F> facets = getFacetsByType(typeId);
    return facets.isEmpty() ? null : facets.iterator().next();
  }

  @Override
  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(FacetTypeId<F> typeId) {
    if (myType2Facets == null) {
      MultiValuesMap<FacetTypeId, Facet> typeToFacets = new MultiValuesMap<FacetTypeId, Facet>();
      for (Facet facet : getAllFacets()) {
        typeToFacets.put(facet.getTypeId(), facet);
      }
      Map<FacetTypeId, Collection<Facet>> typeToFacetsCollection = new HashMap<FacetTypeId, Collection<Facet>>();
      for (FacetTypeId id : typeToFacets.keySet()) {
        final Collection<Facet> facets = typeToFacets.get(id);
        typeToFacetsCollection.put(id, Collections.unmodifiableCollection(facets));
      }
      myType2Facets = typeToFacetsCollection;
    }

    final Collection<F> facets = (Collection<F>)myType2Facets.get(typeId);
    return facets != null ? facets : Collections.<F>emptyList();
  }

  protected void facetsChanged() {
    myChildFacets = null;
    myType2Facets = null;
    mySortedFacets = null;
  }
}
