// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet

import com.intellij.openapi.project.Project

/**
 * Implement this interface to be notified about changes in facets in all project modules. The implementation must be registered in plugin.xml
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;projectFacetListener facet-type="facet-type-id" implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
</pre> *
 * If `facet-type-id` is set to [ID][FacetType.getStringId] of a specific facet type, the listener will be notified about changes
 * in facets of this type only. If it is `"any"` the listener will get events about all facets in the project.
 *
 * @see FacetManagerListener
 */
interface ProjectFacetListener<F : Facet<*>> {
  fun firstFacetAdded(project: Project) {
  }

  fun facetAdded(facet: F) {
  }

  fun beforeFacetRemoved(facet: F) {
  }

  fun facetRemoved(facet: F, project: Project) {
  }

  fun allFacetsRemoved(project: Project) {
  }

  fun facetConfigurationChanged(facet: F) {
  }
}
