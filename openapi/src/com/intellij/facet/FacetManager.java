/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.facet.FacetInfo;

/**
 * @author nik
 */
public abstract class FacetManager implements FacetModel {

  public static FacetManager getInstance(Module module) {
    return module.getComponent(FacetManager.class);
  }

  public abstract void addListener(FacetManagerListener listener);
  public abstract void removeListener(FacetManagerListener listener);

  public abstract ModifiableFacetModel createModifiableModel();

  public abstract void createAndCommitFacets(final FacetInfo[] facetInfos);
}
