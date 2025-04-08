// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public interface FacetsProvider {
  
  Facet @NotNull [] getAllFacets(Module module);

  @NotNull @Unmodifiable
  <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type);

  @Nullable
  <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name);
}
