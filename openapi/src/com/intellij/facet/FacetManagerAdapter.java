/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetManagerAdapter implements FacetManagerListener {
  public void beforeFacetAdded(@NotNull Facet facet) {
  }

  public void beforeFacetRemoved(@NotNull Facet facet) {
  }

  public void facetAdded(@NotNull Facet facet) {
  }

  public void facetRemoved(@NotNull Facet facet) {
  }
}
