/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public abstract class LocalFileSystem extends VirtualFileSystem {
  @NonNls public static final String PROTOCOL = "file";

  public static LocalFileSystem getInstance(){

    return ApplicationManager.getApplication().getComponent(LocalFileSystem.class);
  }

  @Nullable
  public abstract VirtualFile findFileByIoFile(File file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(File file);

  /**
   * Performs a nonrecursive synchronous refresh of specified files
   * @param files files to refresh
   * @since 6.0
   */
  public abstract void refreshIoFiles(Iterable<File> files);

  /**
   * Performs a nonrecursive synchronous refresh of specified files
   * @param files files to refresh
   * @since 6.0
   */
  public abstract void refreshFiles(Iterable<VirtualFile> files);


  public abstract byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException;

  public interface WatchRequest {
    @NotNull String getRootPath();

    @NotNull String getFileSystemRootPath();

    boolean isToWatchRecursively();
  }

  /**
   * Adds this rootFile as the watch root for file system
   * @param rootPath
   * @param toWatchRecursively whether the whole subtree should be monitored
   * @return request handle or null if rootFile does not belong to this file system
   */
  @Nullable
  public abstract WatchRequest addRootToWatch(final @NotNull String rootPath, final boolean toWatchRecursively);

  @NotNull
  public abstract Set<WatchRequest> addRootsToWatch(final @NotNull Collection<String> rootPaths, final boolean toWatchRecursively);

  public abstract void removeWatchedRoots(final @NotNull Collection<WatchRequest> rootsToWatch);

  public abstract void removeWatchedRoot(final @NotNull WatchRequest watchRequest);

  public abstract void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler);
  public abstract void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler);
}