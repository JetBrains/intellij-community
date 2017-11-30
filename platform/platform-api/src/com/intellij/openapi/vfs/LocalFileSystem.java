// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.Processor;
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

  @Nullable
  public abstract VirtualFile findFileByIoFile(@NotNull File file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull File file);

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

  public interface WatchRequest {
    @NotNull
    String getRootPath();

    boolean isToWatchRecursively();
  }

  @Nullable
  public WatchRequest addRootToWatch(@NotNull String rootPath, boolean watchRecursively) {
    Set<WatchRequest> result = addRootsToWatch(singleton(rootPath), watchRecursively);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  @NotNull
  public Set<WatchRequest> addRootsToWatch(@NotNull Collection<String> rootPaths, boolean watchRecursively) {
    if (rootPaths.isEmpty()) {
      return Collections.emptySet();
    }
    else if (watchRecursively) {
      return replaceWatchedRoots(Collections.emptySet(), rootPaths, null);
    }
    else {
      return replaceWatchedRoots(Collections.emptySet(), null, rootPaths);
    }
  }

  public void removeWatchedRoot(@Nullable WatchRequest watchRequest) {
    if (watchRequest != null) {
      removeWatchedRoots(singleton(watchRequest));
    }
  }

  public void removeWatchedRoots(@NotNull Collection<WatchRequest> watchRequests) {
    if (!watchRequests.isEmpty()) {
      replaceWatchedRoots(watchRequests, null, null);
    }
  }

  @Nullable
  public WatchRequest replaceWatchedRoot(@Nullable WatchRequest watchRequest, @NotNull String rootPath, boolean watchRecursively) {
    Set<WatchRequest> requests = watchRequest != null ? singleton(watchRequest) : Collections.emptySet();
    Set<String> roots = singleton(rootPath);
    Set<WatchRequest> result = watchRecursively ? replaceWatchedRoots(requests, roots, null) : replaceWatchedRoots(requests, null, roots);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  @NotNull
  public abstract Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                                        @Nullable Collection<String> recursiveRoots,
                                                        @Nullable Collection<String> flatRoots);

  /**
   * Registers a handler that allows a version control system plugin to intercept file operations in the local file system
   * and to perform them through the VCS tool.
   *
   * @param handler the handler instance.
   */
  public abstract void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  /**
   * Unregisters a handler that allows a version control system plugin to intercept file operations in the local file system
   * and to perform them through the VCS tool.
   *
   * @param handler the handler instance.
   */
  public abstract void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  /** @deprecated use {@link VfsUtilCore#visitChildrenRecursively(VirtualFile, VirtualFileVisitor)} (to be removed in IDEA 2019) */
  public abstract boolean processCachedFilesInSubtree(@NotNull VirtualFile file, @NotNull Processor<VirtualFile> processor);
}