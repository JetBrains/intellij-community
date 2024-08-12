// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManagerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class FacetPointersFacetManagerListener implements FacetManagerListener {

  private final Project myProject;

  public FacetPointersFacetManagerListener(Project project) { myProject = project; }

  @Override
  public void facetAdded(@NotNull Facet facet) {
    FacetPointersManager manager = FacetPointersManager.getInstance(myProject);
    if (manager instanceof FacetPointersManagerImpl managerImpl) {
      managerImpl.refreshPointers();
    }
  }

  @Override
  public void beforeFacetRenamed(@NotNull Facet facet) {
    FacetPointersManager manager = FacetPointersManager.getInstance(myProject);
    if (manager instanceof FacetPointersManagerImpl managerImpl) {
      final FacetPointerImpl pointer = managerImpl.get(FacetPointersManager.constructId(facet));
      if (pointer != null) {
        pointer.refresh();
      }
    }
  }

  @Override
  public void facetRenamed(final @NotNull Facet facet, final @NotNull String oldName) {
    FacetPointersManager manager = FacetPointersManager.getInstance(myProject);
    if (manager instanceof FacetPointersManagerImpl managerImpl) {
      managerImpl.refreshPointers();
    }
  }
}
