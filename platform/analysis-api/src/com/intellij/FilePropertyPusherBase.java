// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
@ApiStatus.Experimental
public abstract class FilePropertyPusherBase<T> implements FilePropertyPusher<T> {
  @Override
  @RequiresReadLock
  public void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T actualValue) throws IOException {
    boolean changed = getFilePropertyKey().setPersistentValue(fileOrDir, actualValue);
    if (changed) {
      propertyChanged(project, fileOrDir, actualValue);
    }
  }

  @RequiresReadLock
  protected abstract void propertyChanged(@NotNull Project project,
                       @NotNull VirtualFile fileOrDir,
                       @NotNull T actualProperty);
}
