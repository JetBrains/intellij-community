// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

public interface WatchedRootsProvider {
  /**
   * While implementing the method make sure that {@link com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl} for each of roots
   * has all children loaded recursively. You can do that explicitly by loading VFS and calling {@link com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl#getChildren()}.
   * This is implicitly required by the file watcher to fire events for the changing descendants of a directory.
   * The indicator that all children are loaded is that {@link com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl#allChildrenLoaded()} returns true
   *
   * @return paths which should be monitored via {@link LocalFileSystem#addRootToWatch(String, boolean)}.
   * @see LocalFileSystem
   */
  @Unmodifiable
  @NotNull Set<String> getRootsToWatch(@NotNull Project project);
}