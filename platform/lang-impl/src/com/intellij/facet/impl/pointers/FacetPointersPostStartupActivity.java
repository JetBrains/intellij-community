// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.pointers;

import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class FacetPointersPostStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    FacetPointersManager manager = FacetPointersManager.getInstance(project);
    if (manager instanceof FacetPointersManagerImpl) {
      FacetPointersManagerImpl managerImpl = (FacetPointersManagerImpl)manager;
      managerImpl.refreshPointers();
    }
  }
}
