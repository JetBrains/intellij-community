/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import java.util.EventListener;

/**
 * @author nik
 */
public interface ProjectWideFacetListener<F extends Facet> extends EventListener {

  void firstFacetAdded();

  void facetAdded(F facet);

  void beforeFacetRemoved(F facet);

  void facetRemoved(F facet);

  void allFacetsRemoved();

  void facetConfigurationChanged(F facet);
}
