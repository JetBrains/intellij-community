/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public class DefaultFacetsProvider implements FacetsProvider {
  public static final FacetsProvider INSTANCE = new DefaultFacetsProvider();

  @NotNull
  public Facet[] getAllFacets(Module module) {
    return FacetManager.getInstance(module).getAllFacets();
  }

  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type) {
    return FacetManager.getInstance(module).getFacetsByType(type);
  }

  @Nullable
  public <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name) {
    return FacetManager.getInstance(module).findFacet(type, name);
  }
}
