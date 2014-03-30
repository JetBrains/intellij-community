/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.Processor;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static java.util.Collections.singleton;

public abstract class LocalFileSystem extends NewVirtualFileSystem {
  @NonNls public static final String PROTOCOL = StandardFileSystems.FILE_PROTOCOL;
  @NonNls public static final String PROTOCOL_PREFIX = StandardFileSystems.FILE_PROTOCOL_PREFIX;

  @SuppressWarnings("UtilityClassWithoutPrivateConstructor")
  private static class LocalFileSystemHolder {
    private static final LocalFileSystem ourInstance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public static LocalFileSystem getInstance() {
    return LocalFileSystemHolder.ourInstance;
  }

  /**
   * Checks whether given file is a symbolic link.
   *
   * @param file a file to check.
   * @return <code>true</code> if the file is a symbolic link, <code>false</code> otherwise
   * @since 11.0
   */
  @Override
  public boolean isSymLink(@NotNull final VirtualFile file) {
    return false;
  }

  /**
   * Checks whether given file is a special file.
   *
   * @param file a file to check.
   * @return <code>true</code> if the file exists and is a special one, <code>false</code> otherwise
   * @since 11.0
   */
  @Override
  public boolean isSpecialFile(@NotNull final VirtualFile file) {
    return false;
  }

  @Nullable
  public abstract VirtualFile findFileByIoFile(@NotNull File file);

  @Nullable
  public abstract VirtualFile findFileByIoFile(@NotNull IFile file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull File file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull IFile ioFile);

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   *
   * @param files files to refresh.
   * @since 6.0
   */
  public abstract void refreshIoFiles(@NotNull Iterable<File> files);

  public abstract void refreshIoFiles(@NotNull Iterable<File> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   *
   * @param files files to refresh.
   * @since 6.0
   */
  public abstract void refreshFiles(@NotNull Iterable<VirtualFile> files);

  public abstract void refreshFiles(@NotNull Iterable<VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /** @deprecated fake root considered harmful (to remove in IDEA 14) */
  public final VirtualFile getRoot() {
    VirtualFile[] roots = ManagingFS.getInstance().getLocalRoots();
    assert roots.length > 0 : SystemInfo.OS_NAME;
    return roots[0];
  }

  public interface WatchRequest {
    @NotNull
    String getRootPath();

    boolean isToWatchRecursively();
  }

  @Nullable
  public WatchRequest addRootToWatch(@NotNull final String rootPath, final boolean watchRecursively) {
    final Set<WatchRequest> result = addRootsToWatch(singleton(rootPath), watchRecursively);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  @NotNull
  public abstract Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean watchRecursively);

  public void removeWatchedRoot(@Nullable final WatchRequest watchRequest) {
    if (watchRequest != null) {
      removeWatchedRoots(singleton(watchRequest));
    }
  }

  public abstract void removeWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests);

  @Nullable
  public WatchRequest replaceWatchedRoot(@Nullable final WatchRequest watchRequest,
                                         @NotNull final String rootPath,
                                         final boolean watchRecursively) {
    final Set<WatchRequest> requests = watchRequest != null ? singleton(watchRequest) : Collections.<WatchRequest>emptySet();
    final Set<WatchRequest> result = watchRecursively ? replaceWatchedRoots(requests, singleton(rootPath), null)
                                                      : replaceWatchedRoots(requests, null, singleton(rootPath));
    return result.size() == 1 ? result.iterator().next() : null;
  }

  public abstract Set<WatchRequest> replaceWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests,
                                                        @Nullable final Collection<String> recursiveRoots,
                                                        @Nullable final Collection<String> flatRoots);

  public abstract void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  public abstract void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  public abstract boolean processCachedFilesInSubtree(@NotNull VirtualFile file, @NotNull Processor<VirtualFile> processor);
}
