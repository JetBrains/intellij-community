// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManagerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FacetPointersFacetManagerListener implements FacetManagerListener {

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
  public void facetRenamed(@NotNull final Facet facet, @NotNull final String oldName) {
    FacetPointersManager manager = FacetPointersManager.getInstance(myProject);
    if (manager instanceof FacetPointersManagerImpl managerImpl) {
      managerImpl.refreshPointers();
    }
  }
}
