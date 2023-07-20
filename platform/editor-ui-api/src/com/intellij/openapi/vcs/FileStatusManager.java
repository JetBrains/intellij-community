// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class FileStatusManager {
  protected static final Logger LOG = Logger.getInstance(FileStatusManager.class);

  public static FileStatusManager getInstance(@NotNull Project project) {
    if (project.isDefault()) {
      LOG.error("Can't create FileStatusManager for default project");
      return new DefaultFileStatusManager();
    }
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
   * @see FileStatusListener
   */
  @CalledInAny
  public abstract @NotNull FileStatus getStatus(@NotNull VirtualFile file);

  /**
   * Notify VCS that file statuses might have changed and need to be updated.
   * <p>
   * Not to be confused with {@link FileStatusListener#fileStatusChanged}.
   * This method can be used by {@link com.intellij.openapi.vcs.impl.FileStatusProvider} implementations to notify VCS about the change
   * in {@link com.intellij.openapi.vcs.impl.FileStatusProvider#getFileStatus(VirtualFile)} output.
   * {@link FileStatusListener#fileStatusChanged} is used by VCS to notify {@link FileStatusManager#getStatus(VirtualFile)} users about the change.
   */
  @CalledInAny
  public abstract void fileStatusesChanged();

  @CalledInAny
  public abstract void fileStatusChanged(@Nullable VirtualFile file);

  public void addFileStatusListener(@NotNull FileStatusListener listener, @NotNull Disposable parentDisposable) {
  }

  public @Nullable Color getNotChangedDirectoryColor(@NotNull VirtualFile file) {
    return getRecursiveStatus(file).getColor();
  }

  /**
   * @see VcsConfiguration#SHOW_DIRTY_RECURSIVELY
   * @see FileStatus#NOT_CHANGED_IMMEDIATE
   * @see FileStatus#NOT_CHANGED_RECURSIVE
   */
  public abstract @NotNull FileStatus getRecursiveStatus(@NotNull VirtualFile file);
}
