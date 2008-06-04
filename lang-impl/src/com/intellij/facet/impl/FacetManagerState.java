package com.intellij.facet.impl;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
*/
public class FacetManagerState {
  private List<FacetState> myFacets = new ArrayList<FacetState>();

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<FacetState> getFacets() {
    return myFacets;
  }

  public void setFacets(final List<FacetState> facets) {
    myFacets = facets;
  }
}
