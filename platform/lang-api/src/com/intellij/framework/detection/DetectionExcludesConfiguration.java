/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.detection;

import com.intellij.framework.FrameworkType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class DetectionExcludesConfiguration {
  public static DetectionExcludesConfiguration getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DetectionExcludesConfiguration.class);
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
