// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class FileStatusManager {
  public static FileStatusManager getInstance(@NotNull Project project) {
    return project.getService(FileStatusManager.class);
  }

  /**
   * Returns color that is associated with passed file in vcs subsystem.
   * <p>
   * Users are discouraged from comparing returned value with a constant, because it might be vcs-specific {@link org.zmlx.hg4idea.provider.HgChangeProvider#RENAMED}
   * or affected by other means {@link com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictFileStatusProvider#MODIFIED_OUTSIDE}.
   *
   * @see com.intellij.openapi.vcs.changes.ChangeListManager#getStatus
   * @see FileStatusFactory
   * @see com.intellij.openapi.vcs.impl.FileStatusProvider
   */
  public abstract FileStatus getStatus(@NotNull VirtualFile file);

  public abstract void fileStatusesChanged();

  public abstract void fileStatusChanged(VirtualFile file);

  /**
   * @deprecated Please use overload with parent disposable
   */
  @Deprecated(forRemoval = true)
  public abstract void addFileStatusListener(@NotNull FileStatusListener listener);

  public abstract void addFileStatusListener(@NotNull FileStatusListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileStatusListener(@NotNull FileStatusListener listener);

  public abstract Color getNotChangedDirectoryColor(@NotNull VirtualFile file);

  /**
   * @see VcsConfiguration#SHOW_DIRTY_RECURSIVELY
   * @see FileStatus#NOT_CHANGED_IMMEDIATE
   * @see FileStatus#NOT_CHANGED_RECURSIVE
   */
  @NotNull
  public FileStatus getRecursiveStatus(@NotNull VirtualFile file) {
    FileStatus status = getStatus(file);
    return status != null ? status : FileStatus.NOT_CHANGED;
  }
}
