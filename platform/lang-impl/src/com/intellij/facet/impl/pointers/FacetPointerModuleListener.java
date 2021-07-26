// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.pointers;

import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FacetPointerModuleListener implements ModuleListener {

  private final Project myProject;

  public FacetPointerModuleListener(Project project) { myProject = project; }

  @Override
  public void moduleAdded(@NotNull Project project, @NotNull Module module) {
    FacetPointersManager manager = FacetPointersManager.getInstance(myProject);
    if (manager instanceof FacetPointersManagerImpl) {
      FacetPointersManagerImpl managerImpl = (FacetPointersManagerImpl)manager;
      managerImpl.refreshPointers();
    }
  }

  @Override
  public void modulesRenamed(@NotNull Project project,
                             @NotNull List<? extends Module> modules,
                             @NotNull Function<? super Module, String> oldNameProvider) {
    FacetPointersManager manager = FacetPointersManager.getInstance(myProject);
    if (manager instanceof FacetPointersManagerImpl) {
      FacetPointersManagerImpl managerImpl = (FacetPointersManagerImpl)manager;
      managerImpl.refreshPointers();
    }
  }
}
