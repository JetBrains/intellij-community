// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class LazyRangeMarkerFactory {
  public static LazyRangeMarkerFactory getInstance(Project project) {
    return project.getService(LazyRangeMarkerFactory.class);
  }

  @NotNull
  public abstract RangeMarker createRangeMarker(@NotNull VirtualFile file, int offset);

  @NotNull
  public abstract RangeMarker createRangeMarker(@NotNull VirtualFile file, int line, int column, boolean persistent);
}
