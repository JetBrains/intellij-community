// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.ProjectFileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class LightEditFileIndexFacade extends ProjectFileIndexFacade {

  LightEditFileIndexFacade(@NotNull Project project) {
    super(project);
  }

  @Override
  public boolean isInProjectScope(@NotNull VirtualFile file) {
    return true;
  }
}
