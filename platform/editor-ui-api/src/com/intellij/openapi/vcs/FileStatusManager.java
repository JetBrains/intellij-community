// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * @author mike
 */
public abstract class FileStatusManager {
  public static FileStatusManager getInstance(@NotNull Project project) {
    return project.getComponent(FileStatusManager.class);
  }

  public abstract FileStatus getStatus(@NotNull VirtualFile file);

  public abstract void fileStatusesChanged();

  public abstract void fileStatusChanged(VirtualFile file);

  public abstract void addFileStatusListener(@NotNull FileStatusListener listener);

  public abstract void addFileStatusListener(@NotNull FileStatusListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileStatusListener(@NotNull FileStatusListener listener);

  public abstract Color getNotChangedDirectoryColor(@NotNull VirtualFile file);

  @NotNull
  public FileStatus getRecursiveStatus(@NotNull VirtualFile file) {
    FileStatus status = getStatus(file);
    return status != null ? status : FileStatus.NOT_CHANGED;
  }
}
