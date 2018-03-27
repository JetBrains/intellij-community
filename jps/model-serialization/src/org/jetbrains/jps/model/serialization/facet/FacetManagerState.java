/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
*/
public class FacetManagerState {
  private List<FacetState> myFacets = new ArrayList<>();

  @Property(surroundWithTag = false)
  @XCollection
  public List<FacetState> getFacets() {
    return myFacets;
  }

  public void setFacets(final List<FacetState> facets) {
    myFacets = facets;
  }
}
