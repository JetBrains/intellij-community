// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class ProjectModalTaskOwner implements ModalTaskOwner {

  private final Project project;

  ProjectModalTaskOwner(@NotNull Project project) {
    this.project = project;
  }

  public @NotNull Project getProject() {
    return project;
  }

  @Override
  public String toString() {
    return "ProjectModalTaskOwner(" + project + ')';
  }
}
