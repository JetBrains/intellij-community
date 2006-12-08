/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import java.util.EventListener;

/**
 * @author nik
 */
public interface FacetExistenceListener extends EventListener {

  void facetAdded();

  void allFacetsRemoved();

}
