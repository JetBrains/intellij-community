// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class PomManager {
  private PomManager() {
  }

  public static @NotNull PomModel getModel(@NotNull Project project) {
    return project.getService(PomModel.class);
  }
}