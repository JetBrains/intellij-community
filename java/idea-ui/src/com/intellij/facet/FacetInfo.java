// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet;

import org.jetbrains.annotations.Nullable;

public final class FacetInfo {
  public static final FacetInfo[] EMPTY_ARRAY = new FacetInfo[0];
  private final FacetType myFacetType;
  private final FacetConfiguration myConfiguration;
  private final FacetInfo myUnderlyingFacet;
  private String myName;

  public FacetInfo(final FacetType facetType, String name, final FacetConfiguration configuration, final FacetInfo underlyingFacet) {
    myName = name;
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

  public @Nullable FacetInfo getUnderlyingFacet() {
    return myUnderlyingFacet;
  }

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }
}
