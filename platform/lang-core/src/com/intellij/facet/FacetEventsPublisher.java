// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

public interface FacetEventsPublisher {
  void fireBeforeFacetAdded(Facet<?> facet);
  void fireBeforeFacetRemoved(Facet<?> facet);
  void fireBeforeFacetRenamed(Facet<?> facet);
  void fireFacetAdded(Facet<?> facet);
  void fireFacetRemoved(Module module, Facet<?> facet);
  void fireFacetRenamed(Facet<?> facet, String newName);
  void fireFacetConfigurationChanged(Facet<?> facet);

  static FacetEventsPublisher getInstance(Project project) {
    return project.getService(FacetEventsPublisher.class);
  }
}
