// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface to be notified about changes in facets in all project modules. The implementation must be registered in plugin.xml
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;projectFacetListener facet-type="facet-type-id" implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * If {@code facet-type-id} is set to {@link FacetType#getStringId ID} of a specific facet type, the listener will be notified about changes
 * in facets of this type only. If it is {@code "any"} the listener will get events about all facets in the project.
 *
 * @see FacetManagerListener
 */
public interface ProjectFacetListener<F extends Facet<?>> {
  default void firstFacetAdded(@NotNull Project project) {
  }

  default void facetAdded(@NotNull F facet) {
  }

  default void beforeFacetRemoved(@NotNull F facet){
  }

  default void facetRemoved(@NotNull F facet, @NotNull Project project){
  }

  default void allFacetsRemoved(@NotNull Project project){
  }

  default void facetConfigurationChanged(@NotNull F facet){
  }
}
