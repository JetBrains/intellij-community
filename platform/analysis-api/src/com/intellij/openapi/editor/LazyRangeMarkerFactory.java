// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class LazyRangeMarkerFactory {
  public static LazyRangeMarkerFactory getInstance(Project project) {
    return project.getService(LazyRangeMarkerFactory.class);
  }

  public abstract @NotNull RangeMarker createRangeMarker(@NotNull VirtualFile file, int offset);

  public abstract @NotNull RangeMarker createRangeMarker(@NotNull VirtualFile file, int line, int column, boolean persistent);
}
