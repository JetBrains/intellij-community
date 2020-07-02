// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.containers.ContainerUtil;
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
 * We have two roles behind the {@link LocalFileSystem} class.
 * <br/>
 * The first role - an API entry point to exchange some local files
 * with a {@link VirtualFile}.
 * <br/>
 * The second role - the implementation of the
 * filesystem itself which is needed to to implement the {@link VirtualFile}
 * and underlying services, this part is used via the {@link com.intellij.openapi.vfs.newvfs.VfsImplUtil}
 */
public abstract class LocalFileSystem extends NewVirtualFileSystem {
  public static final String PROTOCOL = StandardFileSystems.FILE_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.FILE_PROTOCOL_PREFIX;

  private static class LocalFileSystemHolder {
    private static final LocalFileSystem ourInstance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public static LocalFileSystem getInstance() {
    return LocalFileSystemHolder.ourInstance;
  }

  @Nullable
  public abstract VirtualFile findFileByIoFile(@NotNull File file);

  public final @Nullable VirtualFile findFileByNioFile(@NotNull Path file) {
    return findFileByPath(file.toString().replace(File.separatorChar, '/'));
  }

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull File file);

  public final @Nullable VirtualFile refreshAndFindFileByNioFile(@NotNull Path file) {
    return refreshAndFindFileByPath(file.toString().replace(File.separatorChar, '/'));
  }

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   *
   * @param files files to refresh.
   * @see #refreshIoFiles(Iterable, boolean, boolean, Runnable)
   */
  public void refreshIoFiles(@NotNull Iterable<? extends File> files) {
    refreshIoFiles(files, false, false, null);
  }

  public final void refreshNioFiles(@NotNull Iterable<Path> files) {
    refreshIoFiles(ContainerUtil.map(files, Path::toFile), false, false, null);
  }

  /**
   * Performs the refresh of the specified files based on filesystem events that have already been received. To perform refresh reliably
   * for file operations that have just finished (so that related events might not have been generated),
   * use {@link VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, File...)} instead.
   */
  public abstract void refreshIoFiles(@NotNull Iterable<? extends File> files,
                                      boolean async,
                                      boolean recursive,
                                      @Nullable Runnable onFinish);

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   *
   * @param files files to refresh.
   */
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files) {
    refreshFiles(files, false, false, null);
  }

  public abstract void refreshFiles(@NotNull Iterable<? extends VirtualFile> files,
                                    boolean async,
                                    boolean recursive,
                                    @Nullable Runnable onFinish);

  public interface WatchRequest {
    @NotNull @SystemIndependent String getRootPath();

    boolean isToWatchRecursively();
  }

  /**
   * Same as {@link #addRootToWatch(String, boolean)}, but additionally removes watch when {@code disposal} is disposed.
   */
  @Nullable
  public final WatchRequest addRootToWatch(@NotNull String rootPath, boolean watchRecursively, @NotNull Disposable disposable) {
    WatchRequest request = addRootToWatch(rootPath, watchRecursively);
    if (request != null) {
      Disposer.register(disposable, () -> removeWatchedRoot(request));
    }
    return request;
  }

  @Nullable
  public WatchRequest addRootToWatch(@NotNull String rootPath, boolean watchRecursively) {
    Set<WatchRequest> result = addRootsToWatch(singleton(rootPath), watchRecursively);
    return result.size() == 1 ? result.iterator().next() : null;
  }

  /**
   * Same as {@link #addRootsToWatch(Collection, boolean)}, but additionally removes watch when {@code disposal} is disposed.
   */
  @NotNull
  public final Set<WatchRequest> addRootsToWatch(@NotNull Collection<String> rootPaths,
                                                 boolean watchRecursively,
                                                 @NotNull Disposable disposable) {
    Set<WatchRequest> requests = addRootsToWatch(rootPaths, watchRecursively);
    if (!requests.isEmpty()) {
      Disposer.register(disposable, () -> removeWatchedRoots(requests));
    }
    return requests;
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

  public void removeWatchedRoot(@NotNull WatchRequest watchRequest) {
    removeWatchedRoots(singleton(watchRequest));
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

  /**
   * Stops watching given watch requests and starts watching new paths.
   * May do nothing and return the same set of requests when it contains exactly the same paths.
   */
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
}