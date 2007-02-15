/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface FacetManagerListener extends EventListener {

  void beforeFacetAdded(@NotNull Facet facet);

  void beforeFacetRemoved(@NotNull Facet facet);

  void beforeFacetRenamed(@NotNull Facet facet);


  void facetAdded(@NotNull Facet facet);

  void facetRemoved(@NotNull Facet facet);

  void facetRenamed(@NotNull Facet facet);

}
