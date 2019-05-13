// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author mike
 */
public abstract class FileStatusManager {
  public static FileStatusManager getInstance(@NotNull Project project) {
    return project.getComponent(FileStatusManager.class);
  }

  /**
   * Returns color that is associated with passed file in vcs subsystem.
   * <p>
   * Users are discouraged from comparing returned value with a constant, because it might be vcs-specific (HgChangeProvider#RENAMED)
   * or affected by other means (ChangelistConflictFileStatusProvider#MODIFIED_OUTSIDE).
   *
   * @See com.intellij.openapi.vcs.changes.ChangeListManager#getStatus
   * @See com.intellij.openapi.vcs.FileStatusFactory
   * @See com.intellij.openapi.vcs.impl.FileStatusProvider
   */
  public abstract FileStatus getStatus(@NotNull VirtualFile file);

  public abstract void fileStatusesChanged();

  public abstract void fileStatusChanged(VirtualFile file);

  public abstract void addFileStatusListener(@NotNull FileStatusListener listener);

  public abstract void addFileStatusListener(@NotNull FileStatusListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileStatusListener(@NotNull FileStatusListener listener);

  public abstract Color getNotChangedDirectoryColor(@NotNull VirtualFile file);

  /**
   * @See VcsConfiguration#SHOW_DIRTY_RECURSIVELY
   * @See com.intellij.openapi.vcs.FileStatus#NOT_CHANGED_IMMEDIATE
   * @See com.intellij.openapi.vcs.FileStatus#NOT_CHANGED_RECURSIVE
   */
  @NotNull
  public FileStatus getRecursiveStatus(@NotNull VirtualFile file) {
    FileStatus status = getStatus(file);
    return status != null ? status : FileStatus.NOT_CHANGED;
  }
}
