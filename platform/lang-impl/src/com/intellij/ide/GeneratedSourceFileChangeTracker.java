// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class GeneratedSourceFileChangeTracker {
  public static @NotNull GeneratedSourceFileChangeTracker getInstance(@NotNull Project project) {
    return project.getService(GeneratedSourceFileChangeTracker.class);
  }

  public abstract boolean isEditedGeneratedFile(@NotNull VirtualFile file);
}
