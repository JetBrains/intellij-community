// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.detection;

import com.intellij.framework.FrameworkType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DetectionExcludesConfiguration {
  public static DetectionExcludesConfiguration getInstance(@NotNull Project project) {
    return project.getService(DetectionExcludesConfiguration.class);
  }

  /**
   * Suppress detection of framework {@code type} everywhere in the project
   */
  public abstract void addExcludedFramework(@NotNull FrameworkType type);

  /**
   * Suppress detection of framework {@code type} in the specified file or directory
   */
  public abstract void addExcludedFile(@NotNull VirtualFile fileOrDirectory, @Nullable FrameworkType type);

  /**
   * Suppress detection of framework {@code type} in the file or directory specified by {@code url}
   */
  public abstract void addExcludedUrl(@NotNull String url, @Nullable FrameworkType type);

  public abstract boolean isExcludedFromDetection(@NotNull VirtualFile file, @NotNull FrameworkType frameworkType);
  public abstract boolean isExcludedFromDetection(@NotNull FrameworkType frameworkType);
}
