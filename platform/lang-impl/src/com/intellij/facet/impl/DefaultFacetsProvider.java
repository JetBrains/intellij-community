// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class DefaultFacetsProvider implements FacetsProvider {
  public static final FacetsProvider INSTANCE = new DefaultFacetsProvider();

  @Override
  public Facet @NotNull [] getAllFacets(Module module) {
    return FacetManager.getInstance(module).getAllFacets();
  }

  @Override
  public @NotNull <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type) {
    return FacetManager.getInstance(module).getFacetsByType(type);
  }

  @Override
  public @Nullable <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name) {
    return FacetManager.getInstance(module).findFacet(type, name);
  }
}
