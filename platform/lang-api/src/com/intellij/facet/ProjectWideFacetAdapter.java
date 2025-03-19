// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;

public abstract class ProjectWideFacetAdapter<F extends Facet> implements ProjectWideFacetListener<F> {
  @Override
  public void firstFacetAdded() {
  }

  @Override
  public void facetAdded(final @NotNull F facet) {
  }

  @Override
  public void facetConfigurationChanged(final @NotNull F facet) {
  }

  @Override
  public void beforeFacetRemoved(final @NotNull F facet) {
  }

  @Override
  public void facetRemoved(final @NotNull F facet) {
  }

  @Override
  public void allFacetsRemoved() {
  }
}
