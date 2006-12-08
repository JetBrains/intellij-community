/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
*/
public final class FacetInfo {
  public static final FacetInfo[] EMPTY_ARRAY = new FacetInfo[0];
  private FacetType myFacetType;
  private FacetConfiguration myConfiguration;
  private FacetInfo myUnderlyingFacet;

  public FacetInfo(final FacetType facetType, final FacetConfiguration configuration, final FacetInfo underlyingFacet) {
    myFacetType = facetType;
    myConfiguration = configuration;
    myUnderlyingFacet = underlyingFacet;
  }

  public FacetType getFacetType() {
    return myFacetType;
  }

  public FacetConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Nullable
  public FacetInfo getUnderlyingFacet() {
    return myUnderlyingFacet;
  }
}
