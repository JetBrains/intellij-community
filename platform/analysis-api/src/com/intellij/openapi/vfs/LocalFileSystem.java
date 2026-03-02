// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

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

  /** NB: verify with {@link com.intellij.util.io.TrashBin#canMoveToTrash(VirtualFile)} before setting */
  @ApiStatus.Internal
  public static final Key<Boolean> MOVE_TO_TRASH = Key.create("vfs.local.move-to-trash");

  @ApiStatus.Internal
  public static final Key<Consumer<Path>> DELETE_CALLBACK = Key.create("vfs.local.delete-callback");

  private static LocalFileSystem ourInstance;

  public static @NotNull LocalFileSystem getInstance() {
    var instance = ourInstance;
    if (instance == null) {
      instance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
      ourInstance = instance;
    }
    return instance;
  }

  /** Prefer {@link #findFileByNioFile(Path)}. */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public @Nullable VirtualFile findFileByIoFile(@NotNull java.io.File file) {
    return findFileByPath(file.getAbsolutePath());
  }

  /** Prefer {@link #refreshAndFindFileByNioFile(Path)}. */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public @Nullable VirtualFile refreshAndFindFileByIoFile(@NotNull java.io.File file) {
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

  /** Prefer {@link #refreshNioFiles(Iterable)} */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public void refreshIoFiles(@NotNull Iterable<? extends java.io.File> files) {
    refreshIoFiles(files, false, false, null);
  }

  /**
   * See {@link #refreshNioFiles(Iterable, boolean, boolean, Runnable)}.
   */
  public final void refreshNioFiles(@NotNull Iterable<? extends Path> files) {
    refreshNioFiles(files, false, false, null);
  }

  /** Prefer {@link #refreshNioFiles(Iterable, boolean, boolean, Runnable)} */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public void refreshIoFiles(@NotNull Iterable<? extends java.io.File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    refreshNioFiles(ContainerUtil.map(files, java.io.File::toPath), async, recursive, onFinish);
  }

  /**
   * Performs the refresh of the specified files based on filesystem events that have already been received.
   * To perform refresh reliably for file operations that have just finished (so that related events might not have been generated),
   * use {@link VfsUtil#markDirtyAndRefresh} instead.
   */
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
    var result = addRootsToWatch(singleton(rootPath), watchRecursively);
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
    var requests = watchRequest != null ? singleton(watchRequest) : Set.<WatchRequest>of();
    var roots = singleton(rootPath);
    var result = watchRecursively ? replaceWatchedRoots(requests, roots, null) : replaceWatchedRoots(requests, null, roots);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  /**
   * Stops watching given watch requests and starts watching new paths.
   * May do nothing and return the same set of requests when it contains exactly the same paths.
   */
  public abstract @NotNull Set<WatchRequest> replaceWatchedRoots(
    @NotNull Collection<WatchRequest> watchRequests,
    @Nullable Collection<String> recursiveRoots,
    @Nullable Collection<String> flatRoots
  );

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
