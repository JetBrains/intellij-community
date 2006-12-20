/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

/**
 * @author nik
 */
public interface ModifiableFacetModel extends FacetModel {

  void addFacet(Facet facet);
  void removeFacet(Facet facet);

  void commit();

  boolean isModified();

  boolean isNewFacet(Facet facet);
}
