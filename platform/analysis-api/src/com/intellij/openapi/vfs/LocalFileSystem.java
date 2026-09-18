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
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Collections.singleton;

/// The desktop implementation of [VirtualFileSystem] for local files. Here "local" means
/// "accessible via local file operations"; a file itself can be on a mounted network drive.
///
/// Instances of [VirtualFile] that belong to this file system are backed by
/// [`PersistentFS`][com.intellij.openapi.vfs.newvfs.persistent.PersistentFS]. They represent a snapshot of the files, not their actual state.
///
/// New code should not depend on this class. Use [StandardFileSystems#local()] for a lookup, [VfsUtil#findFile(Path, boolean)]
/// or [VirtualFileManager] for a path, [`RefreshQueue`][com.intellij.openapi.vfs.newvfs.RefreshQueue] for a refresh,
/// [WatchRoots] for a watch root, and [VirtualFile#isInLocalFileSystem()] as the predicate.
public abstract class LocalFileSystem extends NewVirtualFileSystem {
  public static final String PROTOCOL = StandardFileSystems.FILE_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.FILE_PROTOCOL_PREFIX;

  /// **NB:** verify with [com.intellij.util.io.TrashBin#canMoveToTrash(VirtualFile)] before setting
  @ApiStatus.Internal
  public static final Key<Boolean> MOVE_TO_TRASH = Key.create("vfs.local.move-to-trash");

  @ApiStatus.Internal
  public static final Key<Consumer<Path>> DELETE_CALLBACK = Key.create("vfs.local.delete-callback");

  private static LocalFileSystem ourInstance;

  /// Prefer [StandardFileSystems#local()] for a lookup, [VfsUtil#findFile(Path, boolean)] or [VirtualFileManager] for a path,
  /// [`RefreshQueue`][com.intellij.openapi.vfs.newvfs.RefreshQueue] for a refresh, [WatchRoots] for a watch root,
  /// and [VirtualFile#isInLocalFileSystem()] as the predicate.
  public static @NotNull LocalFileSystem getInstance() {
    var instance = ourInstance;
    if (instance == null) {
      instance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
      ourInstance = instance;
    }
    return instance;
  }

  /// Prefer [#findFileByNioFile(Path)].
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public @Nullable VirtualFile findFileByIoFile(@NotNull java.io.File file) {
    return findFileByPath(file.getAbsolutePath());
  }

  /// Prefer [#refreshAndFindFileByNioFile(Path)].
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public @Nullable VirtualFile refreshAndFindFileByIoFile(@NotNull java.io.File file) {
    return refreshAndFindFileByPath(file.getAbsolutePath());
  }

  @ApiStatus.Internal
  @Override
  public final boolean isLocal() {
    return true;
  }

  /// Prefer [VirtualFileManager#findFileByNioPath(Path)].
  public @Nullable VirtualFile findFileByNioFile(@NotNull Path file) {
    //TODO RC: we convert Path to String, but down the stack we convert String to Path again -- and such conversion
    //         could be costly (e.g. on Windows). It may worth to think about how to pass the Path down the stack,
    //         to avoid this double-conversion
    return findFileByPath(file.toAbsolutePath().toString());
  }

  /// Prefer [VirtualFileManager#refreshAndFindFileByNioPath(Path)].
  public @Nullable VirtualFile refreshAndFindFileByNioFile(@NotNull Path file) {
    return refreshAndFindFileByPath(file.toAbsolutePath().toString());
  }

  /// Prefer [`RefreshQueue#refreshPaths`][com.intellij.openapi.vfs.newvfs.RefreshQueue#refreshPaths(boolean, boolean, Runnable, Collection)]
  /// or [VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, Path...)].
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public void refreshIoFiles(@NotNull Iterable<? extends java.io.File> files) {
    refreshIoFiles(files, false, false, null);
  }

  /// Prefer [`RefreshQueue#refreshPaths`][com.intellij.openapi.vfs.newvfs.RefreshQueue#refreshPaths(boolean, boolean, Runnable, Collection)]
  /// or [VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, Path...)].
  public final void refreshNioFiles(@NotNull Iterable<? extends Path> files) {
    refreshNioFiles(files, false, false, null);
  }

  /// Prefer [`RefreshQueue#refreshPaths`][com.intellij.openapi.vfs.newvfs.RefreshQueue#refreshPaths(boolean, boolean, Runnable, Collection)]
  /// or [VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, Path...)]. Note that the paths come last there.
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public void refreshIoFiles(@NotNull Iterable<? extends java.io.File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    refreshNioFiles(ContainerUtil.map(files, java.io.File::toPath), async, recursive, onFinish);
  }

  /// Performs the refresh of the specified files based on filesystem events that have already been received.
  /// To perform refresh reliably for file operations that have just finished (so that related events might not have been generated),
  /// use [VfsUtil#markDirtyAndRefresh] instead.
  ///
  /// Prefer [`RefreshQueue#refreshPaths`][com.intellij.openapi.vfs.newvfs.RefreshQueue#refreshPaths(boolean, boolean, Runnable, Collection)].
  /// Note that the paths come last there.
  public abstract void refreshNioFiles(@NotNull Iterable<? extends Path> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /// Performs a non-recursive synchronous refresh of specified files.
  ///
  /// Prefer [`RefreshQueue#refresh`][com.intellij.openapi.vfs.newvfs.RefreshQueue#refresh(boolean, boolean, Runnable, Collection)]
  /// or [VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, VirtualFile...)].
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files) {
    refreshFiles(files, false, false, null);
  }

  /// Prefer [`RefreshQueue#refresh`][com.intellij.openapi.vfs.newvfs.RefreshQueue#refresh(boolean, boolean, Runnable, Collection)]
  /// or [VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, VirtualFile...)]. Note that the files come last there.
  public abstract void refreshFiles(@NotNull Iterable<? extends VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /// Prefer [WatchRoots.Token].
  public interface WatchRequest {
    @NotNull @SystemIndependent String getRootPath();

    boolean isToWatchRecursively();
  }

  /// Prefer [WatchRoots#watch(String, boolean)].
  public @Nullable WatchRequest addRootToWatch(@NotNull String rootPath, boolean watchRecursively) {
    var result = addRootsToWatch(singleton(rootPath), watchRecursively);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  /// Starts watching a given set of roots. Please note that it's a client's responsibility to make sure that
  /// files and directories the client is interested in are loaded into VFS.
  ///
  /// Prefer [WatchRoots#watch(String, boolean)].
  public @NotNull Set<WatchRequest> addRootsToWatch(@NotNull Collection<String> rootPaths, boolean watchRecursively) {
    if (rootPaths.isEmpty()) {
      return Set.of();
    }
    else if (watchRecursively) {
      return replaceWatchedRoots(Set.of(), rootPaths, null);
    }
    else {
      return replaceWatchedRoots(Set.of(), null, rootPaths);
    }
  }

  /// Prefer [WatchRoots.Token#close()].
  public void removeWatchedRoot(@NotNull WatchRequest watchRequest) {
    removeWatchedRoots(singleton(watchRequest));
  }

  /// Prefer [WatchRoots.Token#close()].
  public void removeWatchedRoots(@NotNull Collection<WatchRequest> watchRequests) {
    if (!watchRequests.isEmpty()) {
      replaceWatchedRoots(watchRequests, null, null);
    }
  }

  /// Prefer [WatchRoots#watch(String, boolean)] inside [WatchRoots#batch(Runnable)].
  public @Nullable WatchRequest replaceWatchedRoot(@Nullable WatchRequest watchRequest, @NotNull String rootPath, boolean watchRecursively) {
    var requests = watchRequest != null ? singleton(watchRequest) : Set.<WatchRequest>of();
    var roots = singleton(rootPath);
    var result = watchRecursively ? replaceWatchedRoots(requests, roots, null) : replaceWatchedRoots(requests, null, roots);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  /// Stops watching given watch requests and starts watching new paths.
  /// May do nothing and return the same set of requests when it contains exactly the same paths.
  ///
  /// Prefer [WatchRoots#watch(String, boolean)] inside [WatchRoots#batch(Runnable)].
  public abstract @NotNull Set<WatchRequest> replaceWatchedRoots(
    @NotNull Collection<WatchRequest> watchRequests,
    @Nullable Collection<String> recursiveRoots,
    @Nullable Collection<String> flatRoots
  );

  /// Registers a handler that allows a version control system plugin to intercept file operations in the local file system
  /// and to perform them through the VCS tool.
  ///
  /// Prefer the `com.intellij.vfs.local.fileOperationsHandler` extension point.
  public abstract void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  /// Unregisters a handler that allows a version control system plugin to intercept file operations in the local file system
  /// and to perform them through the VCS tool.
  ///
  /// Prefer the `com.intellij.vfs.local.fileOperationsHandler` extension point.
  public abstract void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);
}
