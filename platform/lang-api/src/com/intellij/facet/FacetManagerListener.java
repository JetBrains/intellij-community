// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Implement this interface and register it in {@link com.intellij.util.messages.MessageBusConnection} for a {@link com.intellij.openapi.module.Module}
 * instance to be notified about changes in facets of a particular module.
 * @see ProjectFacetListener
 */
public interface FacetManagerListener extends EventListener {
  default void beforeFacetAdded(@NotNull Facet facet) {
  }

  default void beforeFacetRemoved(@NotNull Facet facet) {
  }

  default void beforeFacetRenamed(@NotNull Facet facet) {
  }

  default void facetAdded(@NotNull Facet facet) {
  }

  default void facetRemoved(@NotNull Facet facet) {
  }

  default void facetRenamed(@NotNull Facet facet, @NotNull String oldName) {
  }

  default void facetConfigurationChanged(@NotNull Facet facet) {
  }
}
