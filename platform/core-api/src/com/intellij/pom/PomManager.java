// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class PomManager {
  private PomManager() {
  }

  @NotNull
  public static PomModel getModel(@NotNull Project project) {
    return project.getService(PomModel.class);
  }
}