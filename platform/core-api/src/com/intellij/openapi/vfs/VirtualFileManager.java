// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.Topic;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Manages virtual file systems.
 *
 * @see VirtualFileSystem
 */
public abstract class VirtualFileManager implements ModificationTracker {

  /**
   * Consider using {@link VirtualFileManager#VFS_CHANGES_BG} to run your listener on background.
   */
  @Topic.AppLevel
  public static final Topic<BulkFileListener> VFS_CHANGES = new Topic<>(BulkFileListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  @Topic.AppLevel
  @ApiStatus.Experimental
  public static final Topic<BulkFileListenerBackgroundable> VFS_CHANGES_BG =
    new Topic<>(BulkFileListenerBackgroundable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  public static final @NotNull ModificationTracker VFS_STRUCTURE_MODIFICATIONS = () -> getInstance().getStructureModificationCount();

  private static final Supplier<VirtualFileManager> ourInstance = CachedSingletonsRegistry.lazy(() -> {
    return ApplicationManager.getApplication().getService(VirtualFileManager.class);
  });

  /**
   * Gets the instance of {@code VirtualFileManager}.
   *
   * @return {@code VirtualFileManager}
   */
  public static @NotNull VirtualFileManager getInstance() {
    return ourInstance.get();
  }

  /**
   * Gets VirtualFileSystem with the specified protocol.
   *
   * @param protocol String representing the protocol
   * @return {@link VirtualFileSystem}
   * @see VirtualFileSystem#getProtocol
   */
  @Contract("null -> null")
  public abstract VirtualFileSystem getFileSystem(@Nullable String protocol);

  /**
   * The method refreshes the whole VFS, which may take time and produce unrelated events. Use {@link VirtualFile#refresh} instead.
   * <p>
   * Besides, the method is blocking and requires a write lock.
   */
  @ApiStatus.Obsolete
  public abstract long syncRefresh();

  /** The method refreshes the whole VFS, which may take time and produce unrelated events. Use {@link VirtualFile#refresh} instead. */
  @ApiStatus.Obsolete
  public abstract long asyncRefresh(@Nullable Runnable postAction);

  /** The method refreshes the whole VFS, which may take time and produce unrelated events. Use {@link VirtualFile#refresh} instead. */
  @ApiStatus.Obsolete
  public final long asyncRefresh() {
    return asyncRefresh(null);
  }

  /** The method refreshes the whole VFS, which may take time and produce unrelated events. Use {@link VfsUtil#markDirtyAndRefresh} instead. */
  @ApiStatus.Obsolete
  public abstract void refreshWithoutFileWatcher(boolean asynchronous);

  /**
   * Searches for a file specified by the given {@link VirtualFile#getUrl() URL}.
   *
   * @param url the URL to find a file by
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFile#getUrl
   * @see VirtualFileSystem#findFileByPath
   * @see #refreshAndFindFileByUrl
   */
  public @Nullable VirtualFile findFileByUrl(@NonNls @NotNull String url) {
    return null;
  }

  /**
   * Looks for a related {@link VirtualFile} for a given {@link Path}
   *
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFile#getUrl
   * @see VirtualFileSystem#findFileByPath
   * @see #refreshAndFindFileByUrl
   */
  public @Nullable VirtualFile findFileByNioPath(@NotNull Path path) {
    return null;
  }

  /**
   * <p>Refreshes only the part of the file system needed for searching the file by the given URL and finds a file by the given URL.</p>
   *
   * <p>This method is useful when the file was created externally, and you need to find a {@link VirtualFile} corresponding to it.</p>
   *
   * <p>If this method is invoked not from Swing event dispatch thread, then it must not happen inside a read action.</p>
   *
   * @param url the URL
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFileSystem#findFileByPath
   * @see VirtualFileSystem#refreshAndFindFileByPath
   */
  public @Nullable VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    return null;
  }

  /**
   * <p>Refreshes only the part of the file system needed for searching the file by the given URL and finds a file by the given URL.</p>
   *
   * <p>This method is useful when the file was created externally, and you need to find a {@link VirtualFile} corresponding to it.</p>
   *
   * <p>If this method is invoked not from Swing event dispatch thread, then it must not happen inside a read action.</p>
   *
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFileSystem#findFileByPath
   * @see VirtualFileSystem#refreshAndFindFileByPath
   **/
  public @Nullable VirtualFile refreshAndFindFileByNioPath(@NotNull Path path) {
    return null;
  }

  /**
   * @deprecated Prefer {@link #addVirtualFileListener(VirtualFileListener, Disposable)} or other VFS listeners.
   */
  @Deprecated
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * @deprecated When possible, migrate to {@link AsyncFileListener} to process events on a pooled thread.
   * Otherwise, consider using {@link #VFS_CHANGES} message bus topic to avoid early initialization of {@link VirtualFileManager}.
   */
  @Deprecated
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener, @NotNull Disposable parentDisposable);

  /**
   * @deprecated Prefer {@link #addVirtualFileListener(VirtualFileListener, Disposable)} or other VFS listeners.
   */
  @Deprecated
  public abstract void removeVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * Consider using extension point {@code vfs.asyncListener}.
   */
  public abstract void addAsyncFileListener(@NotNull AsyncFileListener listener, @NotNull Disposable parentDisposable);

  public abstract void addAsyncFileListener(@NotNull CoroutineScope coroutineScope, @NotNull AsyncFileListener listener);

  /**
   * Constructs a {@link VirtualFile#getUrl() URL} by specified protocol and path.
   *
   * @param protocol the protocol
   * @param path     the path
   * @return URL
   * @see VirtualFile#getUrl
   */
  public static @NotNull String constructUrl(@NotNull String protocol, @NotNull String path) {
    return protocol + URLUtil.SCHEME_SEPARATOR + path;
  }

  /**
   * Extracts protocol from the given URL. Protocol is a substring from the beginning of the URL till "://".
   *
   * @param url the URL
   * @return protocol or {@code null} if there is no "://" in the URL
   * @see VirtualFileSystem#getProtocol
   */
  public static @Nullable String extractProtocol(@NotNull String url) {
    int index = url.indexOf(URLUtil.SCHEME_SEPARATOR);
    if (index < 0) return null;
    return url.substring(0, index);
  }

  /**
   * @see URLUtil#extractPath(String)
   */
  public static @NotNull String extractPath(@NotNull String url) {
    return URLUtil.extractPath(url);
  }

  public abstract void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener, @NotNull Disposable parentDisposable);

  /** @deprecated Use {@link #addVirtualFileManagerListener(VirtualFileManagerListener, Disposable)} */
  @Deprecated
  public abstract void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener);

  public abstract void notifyPropertyChanged(
    @NotNull VirtualFile virtualFile,
    @VirtualFile.PropName @NotNull String property,
    Object oldValue,
    Object newValue
  );

  /**
   * @return a number that's incremented every time something changes in the VFS, i.e., file hierarchy, names, flags, attributes, contents.
   * This only counts modifications done in the current IDE session.
   * @see #getStructureModificationCount()
   */
  @Override
  public abstract long getModificationCount();

  /**
   * @return a number that's incremented every time something changes in the VFS structure, i.e., file hierarchy or names.
   * This only counts modifications done in the current IDE session.
   * @see #getModificationCount()
   */
  public abstract long getStructureModificationCount();

  @ApiStatus.Internal
  public VirtualFile findFileById(int id) {
    return null;
  }

  @ApiStatus.Internal
  public int[] listAllChildIds(int id) {
    return ArrayUtil.EMPTY_INT_ARRAY;
  }

  @ApiStatus.Internal
  public abstract int storeName(@NotNull String name);

  @ApiStatus.Internal
  public abstract @NotNull CharSequence getVFileName(int nameId);
}
