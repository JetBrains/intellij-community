// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.NotNull;

public abstract class ProjectReloadState {
  public static ProjectReloadState getInstance(@NotNull Project project) {
    return project.getService(ProjectReloadState.class);
  }

  public abstract boolean isAfterAutomaticReload();

  public abstract void onBeforeAutomaticProjectReload();
}
