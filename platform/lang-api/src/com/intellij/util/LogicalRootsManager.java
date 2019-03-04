// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * // TODO: merge with FileReferenceHelper & drop
 *
 * @author spleaner
 * @deprecated use {@link com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper} instead
 */
@Deprecated
public abstract class LogicalRootsManager {

  public static LogicalRootsManager getLogicalRootsManager(@NotNull final Project project) {
    return ServiceManager.getService(project, LogicalRootsManager.class);
  }

  @Nullable
  public abstract LogicalRoot findLogicalRoot(@NotNull final VirtualFile file);

  public abstract List<LogicalRoot> getLogicalRoots();
}
