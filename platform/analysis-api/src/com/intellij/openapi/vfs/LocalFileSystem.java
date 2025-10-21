// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static java.util.Collections.singleton;

/**
 * <p>An implementation of {@link VirtualFileSystem} interface dealing with local files (here, "local" means
 * "accessible via local file operations", files themselves could be located on a mounted network drive).</p>
 *
 * <p>Instances of {@link VirtualFile} belonging to LocalFileSystem are backed by
 * {@link com.intellij.openapi.vfs.newvfs.persistent.PersistentFS PersistentFS} - meaning they represent not an actual state of files,
 * rather a snapshot thereof.</p>
 */
public abstract class LocalFileSystem extends NewVirtualFileSystem {
  public static final String PROTOCOL = StandardFileSystems.FILE_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.FILE_PROTOCOL_PREFIX;

  private static LocalFileSystem ourInstance;

  public static @NotNull LocalFileSystem getInstance() {
    LocalFileSystem instance = ourInstance;
    if (instance == null) {
      instance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
      ourInstance = instance;
    }
    return instance;
  }

  public @Nullable VirtualFile findFileByIoFile(@NotNull File file) {
    return findFileByPath(file.getAbsolutePath());
  }

  public @Nullable VirtualFile refreshAndFindFileByIoFile(@NotNull File file) {
    return refreshAndFindFileByPath(file.getAbsolutePath());
  }

  public @Nullable VirtualFile findFileByNioFile(@NotNull Path file) {
    //TODO RC: we convert Path to String, but down the stack we convert String to Path again -- and such conversion
    //         could be costly (e.g. on Windows). It may worth to think about how to pass the Path down the stack,
    //         to avoid this double-conversion
    return findFileByPath(file.toAbsolutePath().toString());
  }

  public @Nullable VirtualFile refreshAndFindFileByNioFile(@NotNull Path file) {
    return refreshAndFindFileByPath(file.toAbsolutePath().toString());
  }

  /**
   * See {@link #refreshIoFiles(Iterable, boolean, boolean, Runnable)}.
   */
  public void refreshIoFiles(@NotNull Iterable<? extends File> files) {
    refreshIoFiles(files, false, false, null);
  }

  /**
   * See {@link #refreshNioFiles(Iterable, boolean, boolean, Runnable)}.
   */
  public final void refreshNioFiles(@NotNull Iterable<? extends Path> files) {
    refreshNioFiles(files, false, false, null);
  }

  /**
   * Performs the refresh of the specified files based on filesystem events that have already been received. To perform refresh reliably
   * for file operations that have just finished (so that related events might not have been generated),
   * use {@link VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, File...)} instead.
   */
  public abstract void refreshIoFiles(@NotNull Iterable<? extends File> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  public abstract void refreshNioFiles(@NotNull Iterable<? extends Path> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   */
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files) {
    refreshFiles(files, false, false, null);
  }

  public abstract void refreshFiles(@NotNull Iterable<? extends VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  public interface WatchRequest {
    @NotNull @SystemIndependent String getRootPath();

    boolean isToWatchRecursively();
  }

  public @Nullable WatchRequest addRootToWatch(@NotNull String rootPath, boolean watchRecursively) {
    Set<WatchRequest> result = addRootsToWatch(singleton(rootPath), watchRecursively);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  /**
   * Starts watching a given set of roots. Please note that it's a client's responsibility to make sure that
   * files and directories the client is interested in are loaded into VFS.
   */
  public @NotNull Set<WatchRequest> addRootsToWatch(@NotNull Collection<String> rootPaths, boolean watchRecursively) {
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

  public void removeWatchedRoot(@NotNull WatchRequest watchRequest) {
    removeWatchedRoots(singleton(watchRequest));
  }

  public void removeWatchedRoots(@NotNull Collection<WatchRequest> watchRequests) {
    if (!watchRequests.isEmpty()) {
      replaceWatchedRoots(watchRequests, null, null);
    }
  }

  public @Nullable WatchRequest replaceWatchedRoot(@Nullable WatchRequest watchRequest, @NotNull String rootPath, boolean watchRecursively) {
    Set<WatchRequest> requests = watchRequest != null ? singleton(watchRequest) : Collections.emptySet();
    Set<String> roots = singleton(rootPath);
    Set<WatchRequest> result = watchRecursively ? replaceWatchedRoots(requests, roots, null) : replaceWatchedRoots(requests, null, roots);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  /**
   * Stops watching given watch requests and starts watching new paths.
   * May do nothing and return the same set of requests when it contains exactly the same paths.
   */
  public abstract @NotNull Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                                                 @Nullable Collection<String> recursiveRoots,
                                                                 @Nullable Collection<String> flatRoots);

  /**
   * Registers a handler that allows a version control system plugin to intercept file operations in the local file system
   * and to perform them through the VCS tool.
   */
  public abstract void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  /**
   * Unregisters a handler that allows a version control system plugin to intercept file operations in the local file system
   * and to perform them through the VCS tool.
   */
  public abstract void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);
}
