// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FacetManagerState {
  @Property(surroundWithTag = false)
  @XCollection
  public final List<FacetState> facets = new ArrayList<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FacetManagerState state = (FacetManagerState)o;
    return facets.equals(state.facets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(facets);
  }
}
