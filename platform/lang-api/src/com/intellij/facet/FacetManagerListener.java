// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Implement this interface and register it in {@link com.intellij.util.messages.MessageBusConnection} for a {@link com.intellij.openapi.module.Module}
 * instance to be notified about changes in facets of a particular module.
 * @see ProjectFacetListener
 */
public interface FacetManagerListener extends EventListener {
  void beforeFacetAdded(@NotNull Facet facet);

  void beforeFacetRemoved(@NotNull Facet facet);

  void beforeFacetRenamed(@NotNull Facet facet);


  void facetAdded(@NotNull Facet facet);

  void facetRemoved(@NotNull Facet facet);

  void facetRenamed(@NotNull Facet facet, @NotNull String oldName);

  void facetConfigurationChanged(@NotNull Facet facet);
}
