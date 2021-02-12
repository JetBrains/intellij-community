// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated this class isn't used by the platform anymore; if you need to ensure that custom types of roots are handled properly,
 * use relevant extensions instead. If you need to find a source root by a file, use {@link com.intellij.openapi.roots.ProjectFileIndex#getSourceRootForFile},
 * if you need to enumerate all source roots in a project, use {@link ProjectRootManager#getContentSourceRoots()}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public abstract class LogicalRootsManager {

  public static LogicalRootsManager getLogicalRootsManager(@NotNull final Project project) {
    return ServiceManager.getService(project, LogicalRootsManager.class);
  }

  @Nullable
  public abstract LogicalRoot findLogicalRoot(@NotNull final VirtualFile file);

  public abstract List<LogicalRoot> getLogicalRoots();
}
