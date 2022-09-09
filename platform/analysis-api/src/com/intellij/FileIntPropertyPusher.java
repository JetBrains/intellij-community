// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface FileIntPropertyPusher<T> extends FilePropertyPusher<T> {

  @Override
  default void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T actualValue) throws IOException {
    boolean changed = getFileDataKey().setPersistentValue(fileOrDir, actualValue);
    if (changed) {
      propertyChanged(project, fileOrDir, actualValue);
    }
  }

  void propertyChanged(@NotNull Project project,
                       @NotNull VirtualFile fileOrDir,
                       @NotNull T actualProperty);
}