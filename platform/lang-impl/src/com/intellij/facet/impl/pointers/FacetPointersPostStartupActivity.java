// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.pointers;

import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

final class FacetPointersPostStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    FacetPointersManager manager = FacetPointersManager.getInstance(project);
    if (manager instanceof FacetPointersManagerImpl) {
      ((FacetPointersManagerImpl)manager).refreshPointers();
    }
  }
}
